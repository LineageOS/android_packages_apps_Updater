/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
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
import com.android.settingslib.spa.widget.scaffold.MoreOptionsAction
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold

abstract class UpdatesScaffoldActivity : ComponentActivity() {

    private var legacyView: View? = null
    private val refreshEnabled = mutableStateOf(true)

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
                    SettingsScaffold(
                        title = stringResource(R.string.display_name),
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
                                MenuItem(stringResource(R.string.local_update_import)) {
                                    onLocalUpdateClick()
                                }
                                MenuItem(stringResource(R.string.menu_preferences)) {
                                    onPreferencesClick()
                                }
                                MenuItem(stringResource(R.string.menu_show_changelog)) {
                                    onChangelogClick()
                                }
                            }
                        },
                    ) { paddingValues ->
                        AndroidView(
                            factory = { capturedView },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .nestedScroll(rememberNestedScrollInteropConnection()),
                        )
                    }
                }
            }
        }
    }

    override fun <T : View> findViewById(id: Int): T? =
        super.findViewById(id) ?: legacyView?.findViewById(id)

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun onPreferencesClick() {}
    open fun onChangelogClick() {}
}
