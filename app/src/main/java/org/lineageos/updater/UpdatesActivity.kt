/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.icu.text.DateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemProperties
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import org.json.JSONException
import org.lineageos.updater.UpdatesCheckReceiver.Companion.cancelRepeatingUpdatesCheck
import org.lineageos.updater.UpdatesCheckReceiver.Companion.cancelUpdatesCheck
import org.lineageos.updater.UpdatesCheckReceiver.Companion.scheduleRepeatingUpdatesCheck
import org.lineageos.updater.UpdatesCheckReceiver.Companion.updateRepeatingUpdatesCheck
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.controller.UpdaterService.LocalBinder
import org.lineageos.updater.download.DownloadClient
import org.lineageos.updater.download.DownloadClient.DownloadCallback
import org.lineageos.updater.misc.BuildInfoUtils.buildDateTimestamp
import org.lineageos.updater.misc.BuildInfoUtils.buildVersion
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.StringGenerator.getDateLocalized
import org.lineageos.updater.misc.StringGenerator.getDateLocalizedUTC
import org.lineageos.updater.misc.StringGenerator.getTimeLocalized
import org.lineageos.updater.misc.Utils.checkForNewUpdates
import org.lineageos.updater.misc.Utils.getCachedUpdateList
import org.lineageos.updater.misc.Utils.getChangelogURL
import org.lineageos.updater.misc.Utils.getServerURL
import org.lineageos.updater.misc.Utils.getUpdateCheckSetting
import org.lineageos.updater.misc.Utils.hasTouchscreen
import org.lineageos.updater.misc.Utils.isABDevice
import org.lineageos.updater.misc.Utils.isRecoveryUpdateExecPresent
import org.lineageos.updater.misc.Utils.isUpdateCheckEnabled
import org.lineageos.updater.misc.Utils.parseJson
import org.lineageos.updater.model.UpdateInfo
import org.lineageos.updater.model.UpdateStatus
import java.io.File
import java.io.IOException
import java.util.UUID

class UpdatesActivity : UpdatesListActivity() {
    // Views
    private val appBar by lazy { findViewById<AppBarLayout>(R.id.app_bar) }
    private val collapsingToolbar by lazy {
        findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
    }
    private val headerBuildVersion by lazy { findViewById<TextView>(R.id.header_build_version) }
    private val headerBuildDate by lazy { findViewById<TextView>(R.id.header_build_date) }
    private val headerLastCheck by lazy { findViewById<TextView>(R.id.header_last_check) }
    private val headerTitle by lazy { findViewById<TextView>(R.id.header_title) }
    private val noNewUpdatesView by lazy { findViewById<View>(R.id.no_new_updates_view) }
    private val preferences by lazy { findViewById<View>(R.id.preferences) }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.recycler_view) }
    private val refresh by lazy { findViewById<View>(R.id.refresh) }
    private val refreshIconView by lazy { findViewById<View>(R.id.menu_refresh) }
    private val refreshProgress by lazy { findViewById<View>(R.id.refresh_progress) }
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    // Services
    private val uiModeManager by lazy { getSystemService(UiModeManager::class.java) }

    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var adapter: UpdatesListAdapter
    private var updaterService: UpdaterService? = null

    private val refreshAnimation = RotateAnimation(
        0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f
    ).apply {
        interpolator = LinearInterpolator()
        duration = 1000
    }

    private val isTV by lazy {
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    private var toBeExported: UpdateInfo? = null

    private val exportUpdate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let {
                val uri = it.data!!
                exportUpdate(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updates)

        adapter = UpdatesListAdapter(this)
        recyclerView.adapter = adapter

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        val animator = recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS == intent.action) {
                    val downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID)!!
                    handleDownloadStatusChange(downloadId)
                    adapter.notifyItemChanged(downloadId)
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS == intent.action ||
                    UpdaterController.ACTION_INSTALL_PROGRESS == intent.action
                ) {
                    val downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID)
                    adapter.notifyItemChanged(downloadId!!)
                } else if (UpdaterController.ACTION_UPDATE_REMOVED == intent.action) {
                    val downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID)
                    adapter.removeItem(downloadId!!)
                }
            }
        }

        if (!isTV) {
            setSupportActionBar(toolbar)
            supportActionBar?.let {
                it.setDisplayShowTitleEnabled(false)
                it.setDisplayHomeAsUpEnabled(true)
            }
        }

        headerTitle.text = getString(
            R.string.header_title_text,
            buildVersion
        )
        updateLastCheckedString()
        headerBuildVersion.text = getString(R.string.header_android_version, Build.VERSION.RELEASE)
        headerBuildDate.text = getDateLocalizedUTC(
            this,
            DateFormat.LONG, buildDateTimestamp
        )

        if (!isTV) {
            // Switch between header title and appbar title minimizing overlaps
            appBar.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
                var mIsShown = false
                override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
                    val scrollRange = appBarLayout.totalScrollRange
                    if (!mIsShown && scrollRange + verticalOffset < 10) {
                        collapsingToolbar.title = getString(R.string.display_name)
                        mIsShown = true
                    } else if (mIsShown && scrollRange + verticalOffset > 100) {
                        collapsingToolbar.title = null
                        mIsShown = false
                    }
                }
            })

            if (!hasTouchscreen(this)) {
                // This can't be collapsed without a touchscreen
                appBar.setExpanded(false)
            }
        } else {
            refresh.setOnClickListener {
                downloadUpdatesList(
                    true
                )
            }
            preferences.setOnClickListener { showPreferencesDialog() }
        }
    }

    public override fun onStart() {
        super.onStart()

        val intent = Intent(this, UpdaterService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        val intentFilter = IntentFilter().apply {
            addAction(UpdaterController.ACTION_UPDATE_STATUS)
            addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS)
            addAction(UpdaterController.ACTION_INSTALL_PROGRESS)
            addAction(UpdaterController.ACTION_UPDATE_REMOVED)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver, intentFilter
        )
    }

    public override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            broadcastReceiver
        )

        if (updaterService != null) {
            unbindService(connection)
        }

        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_refresh -> {
            downloadUpdatesList(true)
            true
        }
        R.id.menu_preferences -> {
            showPreferencesDialog()
            true
        }
        R.id.menu_show_changelog -> {
            val openUrl = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getChangelogURL(this))
            )
            startActivity(openUrl)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            val binder = service as LocalBinder
            updaterService = binder.service
            adapter.setUpdaterController(updaterService!!.updaterController)
            updatesList
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            adapter.setUpdaterController(null)
            updaterService = null
            adapter.notifyDataSetChanged()
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun loadUpdatesList(jsonFile: File, manualRefresh: Boolean) {
        Log.d(TAG, "Adding remote updates")

        val controller = updaterService!!.updaterController!!
        var newUpdates = false
        val updates = parseJson(jsonFile, true)
        val updatesOnline: MutableList<String?> = ArrayList()
        for (update in updates) {
            newUpdates = newUpdates or controller.addUpdate(update)
            updatesOnline.add(update.downloadId)
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true)

        if (manualRefresh) {
            showSnackbar(
                if (newUpdates) R.string.snack_updates_found else R.string.snack_no_updates_found,
                Snackbar.LENGTH_SHORT
            )
        }
        val updateIds = mutableListOf<String>()
        val sortedUpdates = controller.updates
        if (sortedUpdates.isEmpty()) {
            noNewUpdatesView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noNewUpdatesView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            sortedUpdates.sortedWith { u1: UpdateInfo, u2: UpdateInfo ->
                u2.timestamp.compareTo(u1.timestamp)
            }
            for (update in sortedUpdates) {
                updateIds.add(update.downloadId)
            }
            adapter.setData(updateIds)
            adapter.notifyDataSetChanged()
        }
    }

    private val updatesList: Unit
        get() {
            val jsonFile = getCachedUpdateList(this)
            if (jsonFile.exists()) {
                try {
                    loadUpdatesList(jsonFile, false)
                    Log.d(TAG, "Cached list parsed")
                } catch (e: IOException) {
                    Log.e(TAG, "Error while parsing json list", e)
                } catch (e: JSONException) {
                    Log.e(TAG, "Error while parsing json list", e)
                }
            } else {
                downloadUpdatesList(false)
            }
        }

    private fun processNewJson(json: File, jsonNew: File, manualRefresh: Boolean) {
        try {
            loadUpdatesList(jsonNew, manualRefresh)
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            val millis = System.currentTimeMillis()
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply()
            updateLastCheckedString()
            if (json.exists() && isUpdateCheckEnabled(this) &&
                checkForNewUpdates(json, jsonNew)
            ) {
                updateRepeatingUpdatesCheck(this)
            }
            // In case we set a one-shot check because of a previous failure
            cancelUpdatesCheck(this)
            jsonNew.renameTo(json)
        } catch (e: IOException) {
            Log.e(TAG, "Could not read json", e)
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG)
        } catch (e: JSONException) {
            Log.e(TAG, "Could not read json", e)
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG)
        }
    }

    private fun downloadUpdatesList(manualRefresh: Boolean) {
        val jsonFile = getCachedUpdateList(this)
        val jsonFileTmp = File(jsonFile.absolutePath + UUID.randomUUID())
        val url = getServerURL(this)

        Log.d(TAG, "Checking $url")

        val callback = object : DownloadCallback {
            override fun onFailure(cancelled: Boolean) {
                Log.e(TAG, "Could not download updates list")
                runOnUiThread {
                    if (!cancelled) {
                        showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG)
                    }
                    refreshAnimationStop()
                }
            }

            override fun onResponse(headers: DownloadClient.Headers?) {}

            override fun onSuccess() {
                runOnUiThread {
                    Log.d(TAG, "List downloaded")
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh)
                    refreshAnimationStop()
                }
            }
        }

        val downloadClient = try {
            DownloadClient.Builder()
                .setUrl(url)
                .setDestination(jsonFileTmp)
                .setDownloadCallback(callback)
                .build()
        } catch (exception: IOException) {
            Log.e(TAG, "Could not build download client")
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG)
            return
        }

        refreshAnimationStart()
        downloadClient.start()
    }

    private fun updateLastCheckedString() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000
        val lastCheckString = getString(
            R.string.header_last_updates_check,
            getDateLocalized(this, DateFormat.LONG, lastCheck),
            getTimeLocalized(this, lastCheck)
        )
        headerLastCheck.text = lastCheckString
    }

    private fun handleDownloadStatusChange(downloadId: String) {
        val update = updaterService!!.updaterController!!.getUpdate(downloadId)!!
        when (update.status) {
            UpdateStatus.PAUSED_ERROR -> showSnackbar(
                R.string.snack_download_failed,
                Snackbar.LENGTH_LONG
            )
            UpdateStatus.VERIFICATION_FAILED -> showSnackbar(
                R.string.snack_download_verification_failed,
                Snackbar.LENGTH_LONG
            )
            UpdateStatus.VERIFIED -> showSnackbar(
                R.string.snack_download_verified,
                Snackbar.LENGTH_LONG
            )
            else -> {}
        }
    }

    override fun exportUpdate(update: UpdateInfo) {
        toBeExported = update

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, update.name)
        }

        exportUpdate.launch(intent)
    }

    private fun exportUpdate(uri: Uri) {
        val intent = Intent(this, ExportUpdateService::class.java).apply {
            action = ExportUpdateService.ACTION_START_EXPORTING
            putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, toBeExported!!.file)
            putExtra(ExportUpdateService.EXTRA_DEST_URI, uri)
        }

        startService(intent)
    }

    override fun showSnackbar(stringId: Int, duration: Int) =
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show()

    private fun refreshAnimationStart() {
        if (!isTV) {
            refreshAnimation.repeatCount = Animation.INFINITE
            refreshIconView.startAnimation(refreshAnimation)
            refreshIconView.isEnabled = false
        } else {
            recyclerView.visibility =
                View.GONE
            noNewUpdatesView.visibility = View.GONE
            refreshProgress.visibility = View.VISIBLE
        }
    }

    private fun refreshAnimationStop() {
        if (!isTV) {
            refreshAnimation.repeatCount = 0
            refreshIconView.isEnabled = true
        } else {
            refreshProgress.visibility = View.GONE
            if (adapter.itemCount > 0) {
                recyclerView.visibility = View.VISIBLE
            } else {
                noNewUpdatesView.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPreferencesDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.preferences_dialog, null)

        val autoCheckInterval =
            view.findViewById<Spinner>(R.id.preferences_auto_updates_check_interval)
        val autoDelete = view.findViewById<SwitchCompat>(R.id.preferences_auto_delete_updates)
        val dataWarning = view.findViewById<SwitchCompat>(R.id.preferences_mobile_data_warning)
        val abPerfMode = view.findViewById<SwitchCompat>(R.id.preferences_ab_perf_mode)
        val updateRecovery = view.findViewById<SwitchCompat>(R.id.preferences_update_recovery)

        if (!isABDevice) {
            abPerfMode.visibility = View.GONE
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        autoCheckInterval.setSelection(getUpdateCheckSetting(this))
        autoDelete.isChecked = prefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false)
        dataWarning.isChecked = prefs.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true)
        abPerfMode.isChecked = prefs.getBoolean(Constants.PREF_AB_PERF_MODE, false)

        if (resources.getBoolean(R.bool.config_hideRecoveryUpdate)) {
            // Hide the update feature if explicitly requested.
            // Might be the case of A-only devices using prebuilt vendor images.
            updateRecovery.visibility = View.GONE
        } else if (isRecoveryUpdateExecPresent) {
            updateRecovery.isChecked =
                SystemProperties.getBoolean(Constants.UPDATE_RECOVERY_PROPERTY, false)
        } else {
            // There is no recovery updater script in the device, so the feature is considered
            // forcefully enabled, just to avoid users to be confused and complain that
            // recovery gets overwritten. That's the case of A/B and recovery-in-boot devices.
            updateRecovery.isChecked = true
            updateRecovery.setOnTouchListener(object : View.OnTouchListener {
                private var forcedUpdateToast: Toast? = null

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    forcedUpdateToast?.cancel()
                    forcedUpdateToast = Toast.makeText(
                        applicationContext,
                        getString(R.string.toast_forced_update_recovery), Toast.LENGTH_SHORT
                    ).also {
                        it.show()
                    }
                    return true
                }
            })
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.menu_preferences)
            .setView(view)
            .setOnDismissListener {
                prefs.edit()
                    .putInt(
                        Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                        autoCheckInterval.selectedItemPosition
                    )
                    .putBoolean(Constants.PREF_AUTO_DELETE_UPDATES, autoDelete.isChecked)
                    .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, dataWarning.isChecked)
                    .putBoolean(Constants.PREF_AB_PERF_MODE, abPerfMode.isChecked)
                    .apply()
                if (isUpdateCheckEnabled(this)) {
                    scheduleRepeatingUpdatesCheck(this)
                } else {
                    cancelRepeatingUpdatesCheck(this)
                    cancelUpdatesCheck(this)
                }
                if (isABDevice) {
                    val enableABPerfMode = abPerfMode.isChecked
                    updaterService!!.updaterController!!.setPerformanceMode(enableABPerfMode)
                }
                if (isRecoveryUpdateExecPresent) {
                    val enableRecoveryUpdate = updateRecovery.isChecked
                    SystemProperties.set(
                        Constants.UPDATE_RECOVERY_PROPERTY,
                        enableRecoveryUpdate.toString()
                    )
                }
            }
            .show()
    }

    companion object {
        private const val TAG = "UpdatesActivity"
    }
}
