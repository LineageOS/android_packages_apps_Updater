/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.compose.LocalNavController
import com.android.settingslib.spa.framework.compose.NavControllerWrapper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.deviceinfo.DeviceInfoBanner
import org.lineageos.updater.preferences.PreferencesActivity
import org.lineageos.updater.updates.UpdateList
import org.lineageos.updater.updates.action.AlertDialogState
import org.lineageos.updater.updates.action.UpdateActionDialog
import org.lineageos.updater.updates.action.UpdateActionHandler
import org.lineageos.updater.updates.state.UpdateItemStateMapper
import org.lineageos.updater.updatescheck.UpdatesCheck
import org.lineageos.updater.updatescheck.UpdatesCheckModel

abstract class UpdatesScaffoldActivity : ComponentActivity() {

    private val viewModel: UpdatesViewModel by viewModels { UpdatesViewModel.factory(application) }
    private var activeUpdaterController: UpdaterController? by mutableStateOf(null)
    private var controllerStateVersion: Int by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun setupCompose() {
        setContent {
            val navController = remember {
                object : NavControllerWrapper {
                    override fun navigate(route: String, popUpCurrent: Boolean) {}
                    override fun navigateBack() = finish()
                }
            }

            CompositionLocalProvider(LocalNavController provides navController) {
                SettingsTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    UpdatesScaffoldContent(
                        uiState = uiState,
                        updaterController = activeUpdaterController,
                        controllerStateVersion = controllerStateVersion,
                        onRefreshClick = { onRefreshClick() },
                        onLocalUpdateClick = { onLocalUpdateClick() },
                        onPreferencesClick = {
                            startActivity(
                                Intent(
                                    this@UpdatesScaffoldActivity,
                                    PreferencesActivity::class.java,
                                )
                            )
                        },
                        onControllerStateChanged = { notifyControllerStateChanged() },
                    )
                }
            }
        }
    }

    protected fun setUpdaterController(controller: UpdaterController?) {
        activeUpdaterController = controller
        notifyControllerStateChanged()
    }

    protected fun notifyControllerStateChanged() {
        controllerStateVersion++
    }

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun exportUpdate(update: Update) {}
}

@Composable
private fun UpdatesScaffoldContent(
    uiState: UpdatesUiState,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onControllerStateChanged: () -> Unit,
) {
    val title = getTitleForUpdateStatus(uiState.updates)

    var actionDialog by remember { mutableStateOf<AlertDialogState?>(null) }
    actionDialog?.let { dialog ->
        UpdateActionDialog(
            dialog = dialog,
            onDismiss = { actionDialog = null },
        )
    }

    SettingsScaffold(title = title) { paddingValues ->
        if (isWideScreen()) {
            WideUpdatesScaffold(
                paddingValues = paddingValues,
                updatesCheckModel = uiState.updatesCheckModel,
                updates = uiState.updates,
                updaterController = updaterController,
                controllerStateVersion = controllerStateVersion,
                showDialog = { actionDialog = it },
                onControllerStateChanged = onControllerStateChanged,
                onRefreshClick = onRefreshClick,
                onLocalUpdateClick = onLocalUpdateClick,
                onPreferencesClick = onPreferencesClick,
            )
        } else {
            UpdatesScaffold(
                paddingValues = paddingValues,
                updatesCheckModel = uiState.updatesCheckModel,
                updates = uiState.updates,
                updaterController = updaterController,
                controllerStateVersion = controllerStateVersion,
                showDialog = { actionDialog = it },
                onControllerStateChanged = onControllerStateChanged,
                onRefreshClick = onRefreshClick,
                onLocalUpdateClick = onLocalUpdateClick,
                onPreferencesClick = onPreferencesClick,
            )
        }
    }
}

@Composable
private fun WideUpdatesScaffold(
    paddingValues: PaddingValues,
    updatesCheckModel: UpdatesCheckModel,
    updates: List<Update>,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    showDialog: (AlertDialogState) -> Unit,
    onControllerStateChanged: () -> Unit,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
            )
    ) {
        UpdatesInformationPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding()),
        )
        UpdatesActionPane(
            model = updatesCheckModel,
            updates = updates,
            updaterController = updaterController,
            controllerStateVersion = controllerStateVersion,
            showDialog = showDialog,
            onControllerStateChanged = onControllerStateChanged,
            onRefreshClick = onRefreshClick,
            onLocalUpdateClick = onLocalUpdateClick,
            onPreferencesClick = onPreferencesClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding()),
        )
    }
}

@Composable
private fun UpdatesScaffold(
    paddingValues: PaddingValues,
    updatesCheckModel: UpdatesCheckModel,
    updates: List<Update>,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    showDialog: (AlertDialogState) -> Unit,
    onControllerStateChanged: () -> Unit,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        UpdatesInformationPane()
        UpdatesActionPane(
            model = updatesCheckModel,
            updates = updates,
            updaterController = updaterController,
            controllerStateVersion = controllerStateVersion,
            showDialog = showDialog,
            onControllerStateChanged = onControllerStateChanged,
            onRefreshClick = onRefreshClick,
            onLocalUpdateClick = onLocalUpdateClick,
            onPreferencesClick = onPreferencesClick,
        )
    }
}

@Composable
private fun UpdatesInformationPane(
    modifier: Modifier = Modifier,
) {
    DeviceInfoBanner(modifier = modifier)
}

@Composable
private fun UpdatesActionPane(
    model: UpdatesCheckModel,
    updates: List<Update>,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    showDialog: (AlertDialogState) -> Unit,
    onControllerStateChanged: () -> Unit,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as UpdatesScaffoldActivity
    val networkMonitor = remember { (context.applicationContext as UpdaterApplication).networkMonitor }
    val networkState by networkMonitor.networkState.collectAsState(
        initial = networkMonitor.currentNetworkState,
    )

    val updateItems = remember(
        updates,
        updaterController,
        networkState,
        controllerStateVersion,
    ) {
        val controller = updaterController ?: return@remember emptyList()
        val mapper = UpdateItemStateMapper(context, controller)
        updates.mapNotNull { update ->
            controller.getUpdate(update.downloadId)?.let {
                mapper.map(it, networkState)
            }
        }
    }

    val actionHandler = remember(updaterController) {
        updaterController?.let { controller ->
            UpdateActionHandler(
                activity = activity,
                updaterController = controller,
                exportUpdate = { update -> activity.exportUpdate(update) },
                showDialog = showDialog,
            )
        }
    }

    Column(modifier = modifier) {
        UpdatesCheck(
            model = model,
            onCheckClick = onRefreshClick,
        )
        UpdateList(
            items = updateItems,
            onAction = { action, downloadId ->
                val controller = updaterController ?: return@UpdateList
                val update = controller.getUpdate(downloadId) ?: return@UpdateList
                actionHandler?.perform(action, update)
                onControllerStateChanged()
            },
        )
        UpdatesFooter(
            onLocalUpdateClick = onLocalUpdateClick,
            onPreferencesClick = onPreferencesClick,
        )
    }
}

@Composable
private fun isWideScreen(): Boolean {
    val minWideScreenWidth = 600.dp
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize

    return with(density) { windowSize.width.toDp() >= minWideScreenWidth }
}

@Composable
private fun UpdatesFooter(
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
) {
    val localUpdateSummary = stringResource(R.string.local_update_import_summary)
    val preferencesSummary = stringResource(R.string.preferences_summary)

    Category {
        Preference(object : PreferenceModel {
            override val title = stringResource(R.string.local_update_import)
            override val summary = { localUpdateSummary }
            override val onClick = onLocalUpdateClick
        })
        Preference(object : PreferenceModel {
            override val title = stringResource(R.string.menu_preferences)
            override val summary = { preferencesSummary }
            override val onClick = onPreferencesClick
        })
    }
}

@Composable
private fun getTitleForUpdateStatus(updates: List<Update>): String = when {
    updates.any { it.status == UpdateStatus.UPDATED_NEED_REBOOT } ->
        stringResource(R.string.installing_update_finished)

    updates.any { it.status == UpdateStatus.INSTALLATION_FAILED } ->
        stringResource(R.string.installing_update_error)

    updates.any {
        it.status == UpdateStatus.INSTALLING ||
                it.status == UpdateStatus.INSTALLATION_SUSPENDED
    } -> stringResource(R.string.installing_update)

    else -> stringResource(R.string.display_name)
}
