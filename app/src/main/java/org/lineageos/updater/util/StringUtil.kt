/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import com.android.settingslib.utils.StringUtil as SettingsLibStringUtil

object StringUtil {

    @JvmStatic
    fun getTimeLocalized(context: Context, unixTimestamp: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .localizedBy(getCurrentLocale(context)!!)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(unixTimestamp))

    @JvmStatic
    fun getDateLocalizedUTC(context: Context, style: FormatStyle, unixTimestamp: Long): String =
        DateTimeFormatter.ofLocalizedDate(style)
            .localizedBy(getCurrentLocale(context)!!)
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochSecond(unixTimestamp))

    @JvmStatic
    fun formatBuildDate(context: Context, unixTimestamp: Long): String {
        val instant = Instant.ofEpochSecond(unixTimestamp)
        val skeleton = if (instant.atZone(ZoneOffset.UTC).year == Year.now(ZoneOffset.UTC).value) {
            "MMMd"
        } else {
            "yMMMd"
        }

        return DateFormat.getInstanceForSkeleton(skeleton, getCurrentLocale(context))
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date.from(instant))
    }

    @JvmStatic
    fun formatETA(context: Context, millis: Long): CharSequence =
        SettingsLibStringUtil.formatElapsedTime(context, millis.toDouble(), true, true)

    @JvmStatic
    fun formatSecurityPatch(context: Context, securityPatch: String): String {
        val patchDate = LocalDate.parse(securityPatch)
        return DateFormat.getInstanceForSkeleton("MMMy", getCurrentLocale(context))
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date.from(patchDate.atStartOfDay(ZoneOffset.UTC).toInstant()))
    }

    @JvmStatic
    fun getCurrentLocale(context: Context): Locale? =
        context.resources.configuration.locales.getFirstMatch(context.resources.assets.locales)
}
