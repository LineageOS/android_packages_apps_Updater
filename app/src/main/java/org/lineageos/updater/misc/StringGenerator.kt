/*
 * Copyright (C) 2017-2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater.misc

import android.content.Context
import org.lineageos.updater.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object StringGenerator {
    private const val SECOND_IN_MILLIS = 1000L
    private const val MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60
    private const val HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60

    fun getTimeLocalized(context: Context, unixTimestamp: Long): String {
        val f = DateFormat.getTimeInstance(DateFormat.SHORT, getCurrentLocale(context))
        val date = Date(unixTimestamp * 1000)
        return f.format(date)
    }

    fun getDateLocalized(context: Context, dateFormat: Int, unixTimestamp: Long): String {
        val f = DateFormat.getDateInstance(dateFormat, getCurrentLocale(context))
        val date = Date(unixTimestamp * 1000)
        return f.format(date)
    }

    fun getDateLocalizedUTC(context: Context, dateFormat: Int, unixTimestamp: Long): String {
        val f = DateFormat.getDateInstance(dateFormat, getCurrentLocale(context))
        f.timeZone = TimeZone.getTimeZone("UTC")
        val date = Date(unixTimestamp * 1000)
        return f.format(date)
    }

    fun formatETA(context: Context, millis: Long): String {
        val res = context.resources
        return if (millis >= HOUR_IN_MILLIS) {
            val hours = ((millis + 1800000) / HOUR_IN_MILLIS).toInt()
            res.getQuantityString(R.plurals.eta_hours, hours, hours)
        } else if (millis >= MINUTE_IN_MILLIS) {
            val minutes = ((millis + 30000) / MINUTE_IN_MILLIS).toInt()
            res.getQuantityString(R.plurals.eta_minutes, minutes, minutes)
        } else {
            val seconds = ((millis + 500) / SECOND_IN_MILLIS).toInt()
            res.getQuantityString(R.plurals.eta_seconds, seconds, seconds)
        }
    }

    fun getCurrentLocale(context: Context): Locale {
        return context.resources.configuration.locales
            .getFirstMatch(context.resources.assets.locales)!!
    }
}
