/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun Lottie(
    resId: Int,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
) {
    Lottie(
        spec = LottieCompositionSpec.RawRes(resId),
        modifier = modifier,
        iterations = iterations,
    )
}

@Composable
fun Lottie(
    spec: LottieCompositionSpec,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
) {
    Box(modifier = modifier) { BaseLottie(spec, iterations) }
}

@Composable
private fun BaseLottie(spec: LottieCompositionSpec, iterations: Int) {
    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(composition, iterations = iterations)
    LottieAnimation(
        composition = composition,
        dynamicProperties = LottieColorUtils.getDefaultDynamicProperties(),
        progress = { progress },
    )
}
