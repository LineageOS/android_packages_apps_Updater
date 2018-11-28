/*
 * Copyright (C) 2017 The LineageOS Project
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

    public static String getDevice() {
        return SystemProperties.get(Constants.PROP_DEVICE);
    }
}
