/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.text.SpannableString
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.StringGenerator.formatETA
import org.lineageos.updater.misc.StringGenerator.getCurrentLocale
import org.lineageos.updater.misc.StringGenerator.getDateLocalizedUTC
import org.lineageos.updater.misc.Utils
import org.lineageos.updater.model.UpdateInfo
import org.lineageos.updater.model.UpdateStatus
import java.io.IOException
import java.text.DateFormat
import java.text.NumberFormat
import kotlin.math.roundToInt

class UpdatesListAdapter(private val activity: UpdatesListActivity) :
    RecyclerView.Adapter<UpdatesListAdapter.ViewHolder>() {
    private val alphaDisabledValue: Float
    private var downloadIds: MutableList<String>? = null
    private var selectedDownload: String? = null
    private var updaterController: UpdaterController? = null
    private var infoDialog: AlertDialog? = null

    private enum class Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        DELETE,
        CANCEL_INSTALLATION,
        REBOOT,
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val updateAction = view.findViewById<Button>(R.id.update_action)!!
        val updateMenu = view.findViewById<ImageButton>(R.id.update_menu)!!
        val buildDate = view.findViewById<TextView>(R.id.build_date)!!
        val buildVersion = view.findViewById<TextView>(R.id.build_version)!!
        val buildSize = view.findViewById<TextView>(R.id.build_size)!!
        val progress = view.findViewById<LinearLayout>(R.id.progress)!!
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)!!
        val progressText = view.findViewById<TextView>(R.id.progress_text)!!
        val progressPercent = view.findViewById<TextView>(R.id.progress_percent)!!
    }

    init {
        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.disabledAlpha, tv, true)
        alphaDisabledValue = tv.float
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.update_item_view, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        infoDialog?.dismiss()
    }

    fun setUpdaterController(updaterController: UpdaterController?) {
        this.updaterController = updaterController
        notifyDataSetChanged()
    }

    private fun handleActiveStatus(viewHolder: ViewHolder, update: UpdateInfo) {
        val updaterController = updaterController ?: return

        var canDelete = false
        val downloadId = update.downloadId
        if (updaterController.isDownloading(downloadId)) {
            canDelete = true
            val downloaded = Formatter.formatShortFileSize(
                activity,
                update.file!!.length()
            )
            val total = Formatter.formatShortFileSize(activity, update.fileSize)
            val percentage = NumberFormat.getPercentInstance().format(
                (
                        update.progress / 100f).toDouble()
            )
            viewHolder.progressPercent.text = percentage
            val eta = update.eta
            if (eta > 0) {
                val etaString: CharSequence = formatETA(activity, eta * 1000)
                viewHolder.progressText.text = activity.getString(
                    R.string.list_download_progress_eta_newer, downloaded, total, etaString
                )
            } else {
                viewHolder.progressText.text = activity.getString(
                    R.string.list_download_progress_newer, downloaded, total
                )
            }
            setButtonAction(viewHolder.updateAction, Action.PAUSE, downloadId, true)
            viewHolder.progressBar.isIndeterminate = update.status === UpdateStatus.STARTING
            viewHolder.progressBar.progress = update.progress
        } else if (updaterController.isInstallingUpdate(downloadId)) {
            setButtonAction(viewHolder.updateAction, Action.CANCEL_INSTALLATION, downloadId, true)
            val notAB = !updaterController.isInstallingABUpdate
            viewHolder.progressText.setText(
                if (notAB) {
                    R.string.dialog_prepare_zip_message
                } else if (update.finalizing) {
                    R.string.finalizing_package
                } else {
                    R.string.preparing_ota_first_boot
                }
            )
            viewHolder.progressBar.isIndeterminate = false
            viewHolder.progressBar.progress = update.installProgress
        } else if (updaterController.isVerifyingUpdate(downloadId)) {
            setButtonAction(viewHolder.updateAction, Action.INSTALL, downloadId, false)
            viewHolder.progressText.setText(R.string.list_verifying_update)
            viewHolder.progressBar.isIndeterminate = true
        } else {
            canDelete = true
            setButtonAction(viewHolder.updateAction, Action.RESUME, downloadId, !isBusy)
            val downloaded = Formatter.formatShortFileSize(
                activity,
                update.file!!.length()
            )
            val total = Formatter.formatShortFileSize(activity, update.fileSize)
            val percentage = NumberFormat.getPercentInstance().format(
                (
                        update.progress / 100f).toDouble()
            )
            viewHolder.progressPercent.text = percentage
            viewHolder.progressText.text = activity.getString(
                R.string.list_download_progress_newer, downloaded, total
            )
            viewHolder.progressBar.isIndeterminate = false
            viewHolder.progressBar.progress = update.progress
        }
        viewHolder.updateMenu.setOnClickListener(
            getClickListener(
                update,
                canDelete,
                viewHolder.updateMenu
            )
        )
        viewHolder.progress.visibility = View.VISIBLE
        viewHolder.progressText.visibility = View.VISIBLE
        viewHolder.buildSize.visibility = View.INVISIBLE
    }

    private fun handleNotActiveStatus(viewHolder: ViewHolder, update: UpdateInfo) {
        val updaterController = updaterController ?: return

        val downloadId = update.downloadId
        if (updaterController.isWaitingForReboot(downloadId)) {
            viewHolder.updateMenu.setOnClickListener(
                getClickListener(update, false, viewHolder.updateMenu)
            )
            setButtonAction(viewHolder.updateAction, Action.REBOOT, downloadId, true)
        } else if (update.persistentStatus == UpdateStatus.Persistent.VERIFIED) {
            viewHolder.updateMenu.setOnClickListener(
                getClickListener(update, true, viewHolder.updateMenu)
            )
            setButtonAction(
                viewHolder.updateAction,
                if (Utils.canInstall(update)) Action.INSTALL else Action.DELETE,
                downloadId, !isBusy
            )
        } else if (!Utils.canInstall(update)) {
            viewHolder.updateMenu.setOnClickListener(
                getClickListener(update, false, viewHolder.updateMenu)
            )
            setButtonAction(viewHolder.updateAction, Action.INFO, downloadId, !isBusy)
        } else {
            viewHolder.updateMenu.setOnClickListener(
                getClickListener(update, false, viewHolder.updateMenu)
            )
            setButtonAction(viewHolder.updateAction, Action.DOWNLOAD, downloadId, !isBusy)
        }
        val fileSize = Formatter.formatShortFileSize(activity, update.fileSize)
        viewHolder.buildSize.text = fileSize
        viewHolder.progress.visibility = View.INVISIBLE
        viewHolder.progressText.visibility = View.INVISIBLE
        viewHolder.buildSize.visibility = View.VISIBLE
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        val updaterController = updaterController ?: return

        val downloadIds = downloadIds ?: run {
            viewHolder.updateAction.isEnabled = false
            return@onBindViewHolder
        }

        val downloadId = downloadIds[i]
        val update = updaterController.getUpdate(downloadId) ?: run {
            // The update was deleted
            viewHolder.updateAction.isEnabled = false
            viewHolder.updateAction.setText(R.string.action_download)
            return@onBindViewHolder
        }

        viewHolder.itemView.isSelected = downloadId == selectedDownload

        val activeLayout = when (update.persistentStatus) {
            UpdateStatus.Persistent.UNKNOWN -> update.status === UpdateStatus.STARTING
            UpdateStatus.Persistent.VERIFIED -> update.status === UpdateStatus.INSTALLING
            UpdateStatus.Persistent.INCOMPLETE -> true
            else -> throw RuntimeException("Unknown update status")
        }
        val buildDate = getDateLocalizedUTC(activity, DateFormat.LONG, update.timestamp)
        val buildVersion = activity.getString(
            R.string.list_build_version,
            update.version
        )

        viewHolder.buildDate.text = buildDate
        viewHolder.buildVersion.text = buildVersion
        viewHolder.buildVersion.setCompoundDrawables(null, null, null, null)

        if (activeLayout) {
            handleActiveStatus(viewHolder, update)
        } else {
            handleNotActiveStatus(viewHolder, update)
        }
    }

    override fun getItemCount() = downloadIds?.size ?: 0

    fun setData(downloadIds: MutableList<String>) {
        this.downloadIds = downloadIds
    }

    fun notifyItemChanged(downloadId: String) {
        downloadIds?.let {
            notifyItemChanged(it.indexOf(downloadId))
        }
    }

    fun removeItem(downloadId: String) {
        downloadIds?.let {
            val position = it.indexOf(downloadId)
            it.remove(downloadId)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount)
        }
    }

    private fun startDownloadWithWarning(downloadId: String) {
        val updaterController = updaterController ?: return

        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true)
        if (Utils.isOnWifiOrEthernet(activity) || !warn) {
            updaterController.startDownload(downloadId)
            return
        }
        val checkboxView = LayoutInflater.from(activity).inflate(R.layout.checkbox_view, null)
        val checkbox = checkboxView.findViewById<CheckBox>(R.id.checkbox)
        checkbox.setText(R.string.checkbox_mobile_data_warning)
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_on_mobile_data_title)
            .setMessage(R.string.update_on_mobile_data_message)
            .setView(checkboxView)
            .setPositiveButton(
                R.string.action_download
            ) { _: DialogInterface?, _: Int ->
                if (checkbox.isChecked) {
                    preferences.edit()
                        .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                        .apply()
                    activity.supportInvalidateOptionsMenu()
                }
                updaterController.startDownload(downloadId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setButtonAction(
        button: Button, action: Action, downloadId: String, enabled: Boolean
    ) {
        val updaterController = updaterController ?: return

        val clickListener: View.OnClickListener?
        when (action) {
            Action.DOWNLOAD -> {
                button.setText(R.string.action_download)
                button.isEnabled = enabled
                clickListener = if (enabled) View.OnClickListener {
                    startDownloadWithWarning(downloadId)
                } else {
                    null
                }
            }
            Action.PAUSE -> {
                button.setText(R.string.action_pause)
                button.isEnabled = enabled
                clickListener = if (enabled) View.OnClickListener {
                    updaterController.pauseDownload(downloadId)
                } else {
                    null
                }
            }
            Action.RESUME -> {
                button.setText(R.string.action_resume)
                button.isEnabled = enabled
                val update = updaterController.getUpdate(downloadId)!!
                val canInstall =
                    Utils.canInstall(update) || update.file!!.length() == update.fileSize
                clickListener = if (enabled) {
                    View.OnClickListener {
                        if (canInstall) {
                            updaterController.resumeDownload(downloadId)
                        } else {
                            activity.showSnackbar(
                                R.string.snack_update_not_installable,
                                Snackbar.LENGTH_LONG
                            )
                        }
                    }
                } else {
                    null
                }
            }
            Action.INSTALL -> {
                button.setText(R.string.action_install)
                button.isEnabled = enabled
                val update = updaterController.getUpdate(downloadId)!!
                val canInstall = Utils.canInstall(update)
                clickListener = if (enabled) View.OnClickListener {
                    if (canInstall) {
                        val installDialog = getInstallDialog(downloadId)
                        installDialog?.show()
                    } else {
                        activity.showSnackbar(
                            R.string.snack_update_not_installable,
                            Snackbar.LENGTH_LONG
                        )
                    }
                } else null
            }
            Action.INFO -> {
                button.setText(R.string.action_info)
                button.isEnabled = enabled
                clickListener = if (enabled) {
                    View.OnClickListener { showInfoDialog() }
                } else {
                    null
                }
            }
            Action.DELETE -> {
                button.setText(R.string.action_delete)
                button.isEnabled = enabled
                clickListener = if (enabled) {
                    View.OnClickListener { getDeleteDialog(downloadId)?.show() }
                } else {
                    null
                }
            }
            Action.CANCEL_INSTALLATION -> {
                button.setText(R.string.action_cancel)
                button.isEnabled = enabled
                clickListener = if (enabled) {
                    View.OnClickListener { cancelInstallationDialog.show() }
                } else {
                    null
                }
            }
            Action.REBOOT -> {
                button.setText(R.string.reboot)
                button.isEnabled = enabled
                clickListener = if (enabled) View.OnClickListener {
                    val pm = activity.getSystemService(
                        PowerManager::class.java
                    )
                    pm.reboot(null)
                } else null
            }
        }
        button.alpha = if (enabled) 1f else alphaDisabledValue

        // Disable action mode when a button is clicked
        button.setOnClickListener { v: View? -> clickListener?.onClick(v) }
    }

    private val isBusy: Boolean
        get() {
            val updaterController = updaterController ?: return false

            return updaterController.hasActiveDownloads() ||
                    updaterController.isVerifyingUpdate ||
                    updaterController.isInstallingUpdate
        }

    private fun getDeleteDialog(downloadId: String): AlertDialog.Builder? {
        val updaterController = updaterController ?: return null

        return AlertDialog.Builder(activity)
            .setTitle(R.string.confirm_delete_dialog_title)
            .setMessage(R.string.confirm_delete_dialog_message)
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, _: Int ->
                updaterController.pauseDownload(downloadId)
                updaterController.deleteUpdate(downloadId)
            }
            .setNegativeButton(android.R.string.cancel, null)
    }

    private fun getClickListener(
        update: UpdateInfo,
        canDelete: Boolean, anchor: View
    ) = View.OnClickListener { startActionMode(update, canDelete, anchor) }

    private fun getInstallDialog(downloadId: String): AlertDialog.Builder? {
        val updaterController = updaterController ?: return null

        if (!isBatteryLevelOk) {
            val resources = activity.resources
            val message = resources.getString(
                R.string.dialog_battery_low_message_pct,
                resources.getInteger(R.integer.battery_ok_percentage_discharging),
                resources.getInteger(R.integer.battery_ok_percentage_charging)
            )
            return AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_battery_low_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
        }
        val update = updaterController.getUpdate(downloadId)!!
        val resId = try {
            if (Utils.isABUpdate(update.file!!)) {
                R.string.apply_update_dialog_message_ab
            } else {
                R.string.apply_update_dialog_message
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not determine the type of the update")
            return null
        }
        val buildDate = getDateLocalizedUTC(
            activity,
            DateFormat.MEDIUM, update.timestamp
        )
        val buildInfoText = activity.getString(
            R.string.list_build_version_date,
            update.version, buildDate
        )
        return AlertDialog.Builder(activity)
            .setTitle(R.string.apply_update_dialog_title)
            .setMessage(
                activity.getString(
                    resId, buildInfoText,
                    activity.getString(android.R.string.ok)
                )
            )
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, _: Int ->
                Utils.triggerUpdate(activity, downloadId)
                maybeShowInfoDialog()
            }
            .setNegativeButton(android.R.string.cancel, null)
    }

    private val cancelInstallationDialog: AlertDialog.Builder
        get() = AlertDialog.Builder(activity)
            .setMessage(R.string.cancel_installation_dialog_message)
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, _: Int ->
                val intent = Intent(activity, UpdaterService::class.java)
                intent.action = UpdaterService.ACTION_INSTALL_STOP
                activity.startService(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)

    private fun maybeShowInfoDialog() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val alreadySeen = preferences.getBoolean(Constants.HAS_SEEN_INFO_DIALOG, false)
        if (alreadySeen) {
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.info_dialog_title)
            .setMessage(R.string.info_dialog_message)
            .setPositiveButton(R.string.info_dialog_ok) { _: DialogInterface?, _: Int ->
                preferences.edit()
                    .putBoolean(Constants.HAS_SEEN_INFO_DIALOG, true)
                    .apply()
            }
            .show()
    }

    private fun startActionMode(update: UpdateInfo, canDelete: Boolean, anchor: View) {
        selectedDownload = update.downloadId
        notifyItemChanged(update.downloadId)
        val wrapper = ContextThemeWrapper(
            activity,
            R.style.AppTheme_PopupMenuOverlapAnchor
        )
        val popupMenu = PopupMenu(
            wrapper, anchor, Gravity.NO_GRAVITY,
            R.attr.actionOverflowMenuStyle, 0
        )
        popupMenu.inflate(R.menu.menu_action_mode)
        val menu = popupMenu.menu as MenuBuilder
        menu.findItem(R.id.menu_delete_action).isVisible = canDelete
        menu.findItem(R.id.menu_copy_url).isVisible = update.availableOnline
        menu.findItem(R.id.menu_export_update).isVisible =
            update.persistentStatus == UpdateStatus.Persistent.VERIFIED
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_delete_action -> {
                    getDeleteDialog(update.downloadId)?.show()
                    true
                }
                R.id.menu_copy_url -> {
                    Utils.addToClipboard(
                        activity,
                        activity.getString(R.string.label_download_url),
                        update.downloadUrl,
                        activity.getString(R.string.toast_download_url_copied)
                    )
                    true
                }
                R.id.menu_export_update -> {
                    activity.exportUpdate(update)
                    true
                }
                else -> false
            }
        }
        val helper = MenuPopupHelper(wrapper, menu, anchor)
        helper.show()
    }

    private fun showInfoDialog() {
        val messageString = String.format(
            getCurrentLocale(activity),
            activity.getString(R.string.blocked_update_dialog_message),
            Utils.getUpgradeBlockedURL(activity)
        )
        val message = SpannableString(messageString)
        Linkify.addLinks(message, Linkify.WEB_URLS)
        infoDialog?.dismiss()
        infoDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.blocked_update_dialog_title)
            .setPositiveButton(android.R.string.ok, null)
            .setMessage(message)
            .show().also {
                it.findViewById<TextView>(android.R.id.message)?.let { textView ->
                    textView.movementMethod = LinkMovementMethod.getInstance()
                }
            }
    }

    private val isBatteryLevelOk: Boolean
        get() {
            val intent = activity.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )!!
            if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
                return true
            }
            val percent = (100f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) /
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)).roundToInt()
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val required = if (plugged and BATTERY_PLUGGED_ANY != 0) {
                activity.resources.getInteger(R.integer.battery_ok_percentage_charging)
            } else {
                activity.resources.getInteger(R.integer.battery_ok_percentage_discharging)
            }
            return percent >= required
        }

    companion object {
        private const val TAG = "UpdateListAdapter"

        private const val BATTERY_PLUGGED_ANY =
            (BatteryManager.BATTERY_PLUGGED_AC or
                    BatteryManager.BATTERY_PLUGGED_USB or
                    BatteryManager.BATTERY_PLUGGED_WIRELESS)
    }
}
