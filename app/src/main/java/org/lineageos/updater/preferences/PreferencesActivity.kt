/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.android.settingslib.spa.framework.compose.LocalNavController
import com.android.settingslib.spa.framework.compose.NavControllerWrapper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.R

class PreferencesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SpaLib)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = remember {
                object : NavControllerWrapper {
                    override fun navigate(route: String, popUpCurrent: Boolean) {}
                    override fun navigateBack() = onBackPressedDispatcher.onBackPressed()
                }
            }
            CompositionLocalProvider(LocalNavController provides navController) {
                SettingsTheme {
                    PreferencesScreen()
                }
            }
        }
    }
}
