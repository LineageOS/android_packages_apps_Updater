/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.settingslib.spa.framework.compose.LocalNavController
import com.android.settingslib.spa.framework.compose.NavControllerWrapper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.scaffold.MoreOptionsAction
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.preferences.PreferencesActivity

abstract class UpdatesScaffoldActivity : ComponentActivity() {

    private var legacyView: View? = null
    private val refreshEnabled = mutableStateOf(true)
    private val viewModel: UpdatesViewModel by viewModels { UpdatesViewModel.factory(application) }

    protected fun setRefreshEnabled(enabled: Boolean) {
        refreshEnabled.value = enabled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun setContentView(layoutResID: Int) {
        val capturedView = layoutInflater.inflate(layoutResID, null).also { legacyView = it }

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
                    val updates = uiState.updates
                    val titleText = when {
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
                    SettingsScaffold(
                        title = titleText,
                        actions = {
                            val enabled by refreshEnabled

                            IconButton(
                                onClick = { onRefreshClick() },
                                enabled = enabled,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_menu_refresh),
                                    contentDescription = stringResource(R.string.menu_refresh),
                                )
                            }

                            MoreOptionsAction {
                                MenuItem(stringResource(R.string.menu_show_changelog)) {
                                    onChangelogClick()
                                }
                            }
                        },
                    ) { paddingValues ->
                        val localUpdateSummary =
                            stringResource(R.string.local_update_import_summary)
                        val preferencesSummary =
                            stringResource(R.string.preferences_summary)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .verticalScroll(rememberScrollState())
                        ) {
                            AndroidView(
                                factory = { capturedView },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .nestedScroll(rememberNestedScrollInteropConnection()),
                            )

                            Category {
                                Preference(object : PreferenceModel {
                                    override val title = stringResource(R.string.local_update_import)
                                    override val summary = { localUpdateSummary }
                                    override val onClick = { onLocalUpdateClick() }
                                })
                                Preference(object : PreferenceModel {
                                    override val title = stringResource(R.string.menu_preferences)
                                    override val summary = { preferencesSummary }
                                    override val onClick = {
                                        startActivity(
                                            Intent(
                                                this@UpdatesScaffoldActivity,
                                                PreferencesActivity::class.java,
                                            )
                                        )
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun <T : View> findViewById(id: Int): T? =
        super.findViewById(id) ?: legacyView?.findViewById(id)

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun onChangelogClick() {}
}
