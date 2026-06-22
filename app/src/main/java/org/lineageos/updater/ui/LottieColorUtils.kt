/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

internal object LottieColorUtils {
    @Composable
    private fun createOnSurfaceFilter(): LottieDynamicProperty<ColorFilter> {
        val color = MaterialTheme.colorScheme.onSurface
        return rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            keyPath = arrayOf("**", ".onSurface", "**"),
        ) {
            PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
    }

    @Composable
    fun getDefaultDynamicProperties() =
        rememberLottieDynamicProperties(createOnSurfaceFilter())
}
