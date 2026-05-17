/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.deviceinfo

import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsShape.CornerExtraLarge1
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.R
import kotlin.math.max
import kotlin.math.roundToInt

// Brand guide: "Mark height based on text x-height". Approximate Roboto x-height from font size.
private const val MARK_X_HEIGHT_RATIO = 0.55f

// Brand guide: "Do not warp, transform". Derive width from height to keep logo proportions.
private const val MARK_WIDTH_MULTIPLIER = 2.5f

// Brand guide: "higher numbers' lower edges". Scale the gap from the mark, not a fixed dp.
private const val VERSION_MARK_SPACING_RATIO = 0.10f

// Pattern: preferred circle radius before snapping the pattern to the card height.
private const val PATTERN_BASE_RADIUS_DP = 25

// Pattern: keep each tile proportional to the circle radius on every screen size.
private const val PATTERN_TILE_RADIUS_RATIO = 6f

private const val PATTERN_SHADER_SRC =
    """ uniform float iTileSize;
    uniform float iRadius;
    uniform float2 iResolution;
    layout(color) uniform half4 iPatternColor;
    layout(color) uniform half4 iSheenColor;

    float circleAlpha(float2 point, float2 center, float radius) {
        return smoothstep(radius + 1.0, radius - 1.0, length(point - center));
    }

    float bottomSemiAlpha(float2 point, float2 center, float radius) {
        return circleAlpha(point, center, radius) * step(center.y, point.y);
    }

    float rightSemiAlpha(float2 point, float2 center, float radius) {
        return circleAlpha(point, center, radius) * step(center.x, point.x);
    }

    half4 main(float2 fragCoord) {
        float tileSize = iTileSize;
        float radius = iRadius;
        float2 local = mod(fragCoord, tileSize);
        float hit = 0.0;

        /* Accumulate coverage from this tile and its 8 neighbours. */
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                float2 point = local - float2(float(dx), float(dy)) * tileSize;

                /* Full circles. */
                hit = max(hit, circleAlpha(point, float2(tileSize / 3.0, 0.0), radius));
                hit = max(
                    hit,
                    circleAlpha(point, float2(tileSize * 2.0 / 3.0, tileSize / 3.0), radius)
                );
                hit = max(hit, circleAlpha(point, float2(0.0, tileSize * 2.0 / 3.0), radius));

                /* Bottom semicircles. */
                hit = max(
                    hit,
                    bottomSemiAlpha(
                        point,
                        float2(tileSize / 3.0, tileSize * 2.0 / 3.0),
                        radius
                    )
                );
                hit = max(hit, bottomSemiAlpha(point, float2(tileSize * 2.0 / 3.0, 0.0), radius));
                hit = max(
                    hit,
                    bottomSemiAlpha(point, float2(0.0, tileSize / 3.0), radius)
                );
                hit = max(
                    hit,
                    bottomSemiAlpha(point, float2(tileSize / 2.0, tileSize / 2.0), radius)
                );
                hit = max(
                    hit,
                    bottomSemiAlpha(
                        point,
                        float2(tileSize * 5.0 / 6.0, tileSize * 5.0 / 6.0),
                        radius
                    )
                );
                hit = max(
                    hit,
                    bottomSemiAlpha(point, float2(tileSize / 6.0, tileSize / 6.0), radius)
                );

                /* Right semicircles. */
                hit = max(
                    hit,
                    rightSemiAlpha(point, float2(tileSize / 2.0, tileSize * 5.0 / 6.0), radius)
                );
                hit = max(
                    hit,
                    rightSemiAlpha(point, float2(tileSize * 5.0 / 6.0, tileSize / 6.0), radius)
                );
                hit = max(
                    hit,
                    rightSemiAlpha(point, float2(tileSize / 6.0, tileSize / 2.0), radius)
                );
                hit = max(
                    hit,
                    rightSemiAlpha(
                        point,
                        float2(tileSize * 2.0 / 3.0, tileSize * 2.0 / 3.0),
                        radius
                    )
                );
                hit = max(hit, rightSemiAlpha(point, float2(0.0, 0.0), radius));
                hit = max(
                    hit,
                    rightSemiAlpha(point, float2(tileSize / 3.0, tileSize / 3.0), radius)
                );
            }
        }

        /* Layer 1 — tiled shapes at 10% opacity. */
        float patternAlpha = 0.10 * hit * iPatternColor.a;
        half4 pattern = half4(iPatternColor.rgb * patternAlpha, patternAlpha);

        /*
         * Layer 2 — linear sheen, clipped to the same pattern coverage.
         * The extra 16% multiplier matches the exported design opacity.
         */
        float2 gradEnd = float2(iResolution.x * 1.0314, iResolution.y * 0.9208);
        float2 dir = gradEnd;
        float t = clamp(dot(fragCoord, dir) / dot(dir, dir), 0.0, 1.0);

        float gradAlpha;
        if (t <= 0.326923) {
            gradAlpha = 0.16;
        } else if (t <= 0.66) {
            gradAlpha = mix(0.16, 1.0, (t - 0.326923) / (0.66 - 0.326923));
        } else {
            gradAlpha = mix(1.0, 0.08, (t - 0.66) / (1.0 - 0.66));
        }

        float sheenAlpha = gradAlpha * 0.16 * hit * iSheenColor.a;
        half4 sheen = half4(iSheenColor.rgb * sheenAlpha, sheenAlpha);

        /* Composite sheen over the base pattern. */
        return sheen + pattern * (1.0 - sheen.a);
    }
"""

@Composable
fun UpdaterCard(
    buildVersion: String,
    androidVersion: String,
    buildDate: String,
    securityPatch: String,
    modifier: Modifier = Modifier,
    shape: Shape = CornerExtraLarge1,
) {
    val brandColor = colorResource(R.color.brand_primary)
    val onBrandColor = colorResource(R.color.on_brand_surface)
    val patternColor = colorResource(R.color.brand_pattern)
    val sheenColor = colorResource(R.color.brand_sheen)
    val brandName = stringResource(R.string.brand_name)

    val density = LocalDensity.current
    val displayMedium = MaterialTheme.typography.displayMedium

    val versionStyle = remember(displayMedium) {
        /*
         * Brand guide: "Roboto Light version text, spaced in 8%".
         */
        displayMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.08.em,
            lineHeight = displayMedium.fontSize,
        )
    }

    val markHeight = remember(versionStyle, density) {
        with(density) { (versionStyle.fontSize.toPx() * MARK_X_HEIGHT_RATIO).toDp() }
    }
    val markWidth = markHeight * MARK_WIDTH_MULTIPLIER

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = onBrandColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brandColor)
                .updaterHeaderPattern(
                    patternColor = patternColor,
                    sheenColor = sheenColor,
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SettingsDimension.paddingLarge),
                ) {
                    Image(
                        painter = painterResource(R.drawable.lineage_mark_tight),
                        contentDescription = brandName,
                        modifier = Modifier
                            .width(markWidth)
                            .alignBy { it.measuredHeight },
                        contentScale = ContentScale.FillWidth,
                        // Brand guide: "Use white when on dark backgrounds".
                        colorFilter = ColorFilter.tint(onBrandColor),
                    )

                    Spacer(modifier = Modifier.width(markWidth * VERSION_MARK_SPACING_RATIO))

                    Text(
                        text = buildVersion,
                        style = versionStyle,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                Spacer(modifier = Modifier.height(SettingsSpace.medium5))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SettingsDimension.paddingLarge,
                            vertical = SettingsDimension.paddingLarge,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDimension.paddingLarge),
                ) {
                    InfoColumn(
                        label = stringResource(
                            R.string.header_build_version,
                            brandName,
                            buildVersion,
                        ),
                        value = stringResource(R.string.header_android_version, androidVersion),
                    )
                    InfoColumn(
                        label = stringResource(R.string.build_date),
                        value = buildDate,
                    )
                    InfoColumn(
                        label = stringResource(R.string.security_update),
                        value = securityPatch,
                    )
                }
            }
        }
    }
}

private fun Modifier.updaterHeaderPattern(
    patternColor: Color,
    sheenColor: Color,
): Modifier = drawWithCache {
    /*
     * Snap radius to height so each border lands at a circle start or center,
     * avoiding random cropped arcs.
     */
    val baseRadiusPx = PATTERN_BASE_RADIUS_DP.dp.toPx()
    val radiusSteps = max(1, (size.height / baseRadiusPx).roundToInt())
    val radiusPx = size.height / radiusSteps
    val tileSizePx = radiusPx * PATTERN_TILE_RADIUS_RATIO

    val shader = RuntimeShader(PATTERN_SHADER_SRC).apply {
        setFloatUniform("iTileSize", tileSizePx)
        setFloatUniform("iRadius", radiusPx)
        setFloatUniform("iResolution", size.width, size.height)
        setColorUniform("iPatternColor", patternColor.toArgb())
        setColorUniform("iSheenColor", sheenColor.toArgb())
    }
    val brush = ShaderBrush(shader)

    onDrawBehind {
        drawRect(brush)
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall2),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Normal,
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@UiModePreviews
@Composable
private fun UpdaterCardPreview() {
    SettingsTheme {
        UpdaterCard(
            buildVersion = "23.2",
            androidVersion = "16",
            buildDate = "Feb 20",
            securityPatch = "Feb 2026",
            modifier = Modifier.padding(SettingsDimension.itemPadding),
        )
    }
}
