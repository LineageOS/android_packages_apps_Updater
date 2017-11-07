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

    private static boolean isCompatible(String current, String requirement) {
        if (requirement == null) {
            return true;
        }
        String[] versions = requirement.split("|");
        for (String version: versions) {
            if (version.equals("*") || version.equals(current)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBootloaderCompatible(Map<String, String> requirements) {
        String bootloaderCur = SystemProperties.get("ro.bootloader");
        String bootloaderReq = requirements.get("ro.build.expect.bootloader");
        return isCompatible(bootloaderCur, bootloaderReq);
    }

    public static boolean isModemCompatible(Map<String, String> requirements) {
        String modemCur = SystemProperties.get("ro.modem");
        String modemReq = requirements.get("ro.build.expect.modem");
        return isCompatible(modemCur, modemReq);
    }

    public static boolean isUpdateCompatible(Map<String, String> requirements) {
        return isBootloaderCompatible(requirements) && isModemCompatible(requirements);
    }

    public static AlertDialog.Builder getDialog(Context context, Map<String, String> requirements) {
        boolean bootloaderCompatible = isBootloaderCompatible(requirements);
        boolean modemCompatible = isModemCompatible(requirements);

        String message;
        if (!bootloaderCompatible && !modemCompatible) {
            String requiredBootloaders = requirements.get("ro.build.expect.bootloader")
                    .replace("|", " ,");
            String requiredModems = requirements.get("ro.build.expect.modem")
                    .replace("|", " ,");
            message = context.getString(R.string.requirements_bootloader_modem_message,
                    requiredBootloaders, requiredModems);
        } else if (!bootloaderCompatible) {
            String requiredBootloaders = requirements.get("ro.build.expect.bootloader")
                    .replace("|", " ,");
            message = context.getString(R.string.requirements_bootloader_message,
                    requiredBootloaders);
        } else if (modemCompatible) {
            String requiredModems = requirements.get("ro.build.expect.modem")
                    .replace("|", " ,");
            message = context.getString(R.string.requirements_modem_message,
                    requiredModems);
        } else {
            return null;
        }

        return new AlertDialog.Builder(context)
                .setTitle(R.string.requirements_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null);
    }
}
