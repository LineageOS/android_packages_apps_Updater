/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.misc;

import android.content.Context;
import android.content.res.Resources;

import org.lineageos.updater.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class StringGenerator {

    private StringGenerator() {
    }

    public static String getTimeLocalized(Context context, long unixTimestamp) {
        DateFormat f = DateFormat.getTimeInstance(DateFormat.SHORT, getCurrentLocale(context));
        Date date = new Date(unixTimestamp * 1000);
        return f.format(date);
    }

    public static String getDateLocalized(Context context, int dateFormat, long unixTimestamp) {
        DateFormat f = DateFormat.getDateInstance(dateFormat, getCurrentLocale(context));
        Date date = new Date(unixTimestamp * 1000);
        return f.format(date);
    }

    public static String getDateLocalizedUTC(Context context, int dateFormat, long unixTimestamp) {
        DateFormat f = DateFormat.getDateInstance(dateFormat, getCurrentLocale(context));
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date(unixTimestamp * 1000);
        return f.format(date);
    }

    public static String formatETA(Context context, long millis) {
        final long SECOND_IN_MILLIS = 1000;
        final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
        final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
        Resources res = context.getResources();
        if (millis >= HOUR_IN_MILLIS) {
            final int hours = (int) ((millis + 1800000) / HOUR_IN_MILLIS);
            return res.getQuantityString(R.plurals.eta_hours, hours, hours);
        } else if (millis >= MINUTE_IN_MILLIS) {
            final int minutes = (int) ((millis + 30000) / MINUTE_IN_MILLIS);
            return res.getQuantityString(R.plurals.eta_minutes, minutes, minutes);
        } else {
            final int seconds = (int) ((millis + 500) / SECOND_IN_MILLIS);
            return res.getQuantityString(R.plurals.eta_seconds, seconds, seconds);
        }
    }

    public static Locale getCurrentLocale(Context context) {
        return context.getResources().getConfiguration().getLocales()
                .getFirstMatch(context.getResources().getAssets().getLocales());
    }
}
