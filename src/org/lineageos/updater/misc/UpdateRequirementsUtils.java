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

import android.content.Context;
import android.os.SystemProperties;
import android.support.v7.app.AlertDialog;

import com.google.android.collect.Maps;

import org.lineageos.updater.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class UpdateRequirementsUtils {

    private static final String PROP_BASEBAND = "ro.baseband";
    private static final String PROP_BOOTLOADER = "ro.bootloader";
    private static final String PROP_MODEM = "ro.modem";
    private static final String PROP_TRUSTZONE = "ro.trustzone";

    private static final String PROP_BUILD_BASEBAND = "ro.build.expect.baseband";
    private static final String PROP_BUILD_BOOTLOADER = "ro.build.expect.bootloader";
    private static final String PROP_BUILD_MODEM = "ro.build.expect.modem";
    private static final String PROP_BUILD_TRUSTZONE = "ro.build.expect.trustzone";

    public static Map<String, String> getRequirements(File update) throws IOException {
        ZipFile zipFile = null;
        InputStream inputStream = null;
        try {
            zipFile = new ZipFile(update);
            ZipEntry entry = zipFile.getEntry("system/build.prop");
            if (entry == null) {
                return null;
            }
            inputStream = zipFile.getInputStream(entry);
            Properties prop = new Properties();
            prop.load(inputStream);
            Map<String, String> requirements = Maps.newHashMap();
            for (String key : prop.stringPropertyNames()) {
                if (key.startsWith("ro.build.expect.")) {
                    requirements.put(key, prop.getProperty(key));
                }
            }

            return requirements;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private static boolean isCompatible(String current, String requirements) {
        if (requirements == null || current == null) {
            return true;
        }
        String[] versions = requirements.split("|");
        for (String version: versions) {
            if (version.equals("*") || version.equals(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBootloaderCompatible(Map<String, String> requirements) {
        String bootloaderCur = SystemProperties.get(PROP_BOOTLOADER);
        String bootloaderReq = requirements.get(PROP_BUILD_BOOTLOADER);
        return isCompatible(bootloaderCur, bootloaderReq);
    }

    private static boolean isModemCompatible(Map<String, String> requirements) {
        String modemCur = SystemProperties.get(PROP_MODEM);
        String modemReq = requirements.get(PROP_BUILD_MODEM);
        return isCompatible(modemCur, modemReq);
    }

    private static boolean isBasebandCompatible(Map<String, String> requirements) {
        String basebandCur = SystemProperties.get(PROP_BASEBAND);
        String basebandReq = requirements.get(PROP_BUILD_BASEBAND);
        return isCompatible(basebandCur, basebandReq);
    }

    private static boolean isTrustzoneCompatible(Map<String, String> requirements) {
        String basebandCur = SystemProperties.get(PROP_TRUSTZONE);
        String basebandReq = requirements.get(PROP_BUILD_TRUSTZONE);
        return isCompatible(basebandCur, basebandReq);
    }

    public static boolean isUpdateCompatible(Map<String, String> requirements) {
        return isBootloaderCompatible(requirements)
                && isModemCompatible(requirements)
                && isBasebandCompatible(requirements)
                && isTrustzoneCompatible(requirements);
    }

    public static AlertDialog.Builder getDialog(Context context, Map<String, String> requirements) {
        boolean bootloaderCompatible = isBootloaderCompatible(requirements);
        boolean modemCompatible = isModemCompatible(requirements);
        boolean basebandCompatible = isBasebandCompatible(requirements);
        boolean trustzoneCompatible = isTrustzoneCompatible(requirements);

        String message = context.getString(R.string.requirements_message);
        if (!bootloaderCompatible) {
            String required = requirements.get(PROP_BUILD_BOOTLOADER)
                    .replace("|", "\n");
            message += "\n";
            message += context.getString(R.string.requirements_bootloader, required);
        }
        if (!modemCompatible) {
            String required = requirements.get(PROP_BUILD_MODEM)
                    .replace("|", "\n");
            message += "\n";
            message += context.getString(R.string.requirements_modem, required);
        }
        if (!basebandCompatible) {
            String required = requirements.get(PROP_BUILD_BASEBAND)
                    .replace("|", "\n");
            message += "\n";
            message += context.getString(R.string.requirements_baseband, required);
        }
        if (!trustzoneCompatible) {
            String required = requirements.get(PROP_BUILD_TRUSTZONE)
                    .replace("|", "\n");
            message += "\n";
            message += context.getString(R.string.requirements_trustzone, required);
        }

        return new AlertDialog.Builder(context)
                .setTitle(R.string.requirements_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null);
    }
}
