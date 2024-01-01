/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.misc;

import android.os.SystemProperties;

public final class BuildInfoUtils {

    private BuildInfoUtils() {
    }

    public static long getBuildDateTimestamp() {
        return SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
    }

    public static String getBuildVersion() {
        return SystemProperties.get(Constants.PROP_BUILD_VERSION);
    }
}
