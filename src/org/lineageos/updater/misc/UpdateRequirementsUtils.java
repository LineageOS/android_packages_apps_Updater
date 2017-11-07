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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.lineageos.updater.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class UpdateRequirementsUtils {

    private static final String BUILD_PROP_PATH = "system/build.prop";
    private static final String PROP_BUILD_PREFIX = "ro.build.expect.";
    private static final String REQUIREMENTS_SEPARATOR = "|";

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
            Map<String, String> requirements = new HashMap<>();
            zipFile = new ZipFile(update);
            ZipEntry entry = zipFile.getEntry(BUILD_PROP_PATH);
            if (entry == null) {
                return requirements;
            }
            inputStream = zipFile.getInputStream(entry);
            Properties prop = new Properties();
            prop.load(inputStream);
            for (String key : prop.stringPropertyNames()) {
                if (key.startsWith(PROP_BUILD_PREFIX)) {
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
        String[] versions = requirements.split(REQUIREMENTS_SEPARATOR);
        for (String version: versions) {
            if (version.equals("*") || version.equals(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBootloaderCompatible(Map<String, String> requirements) {
        return isCompatible(SystemProperties.get(PROP_BOOTLOADER),
                requirements.get(PROP_BUILD_BOOTLOADER));
    }

    private static boolean isModemCompatible(Map<String, String> requirements) {
        return isCompatible(SystemProperties.get(PROP_MODEM),
                requirements.get(PROP_BUILD_MODEM));
    }

    private static boolean isBasebandCompatible(Map<String, String> requirements) {
        return isCompatible(SystemProperties.get(PROP_BASEBAND),
                requirements.get(PROP_BUILD_BASEBAND));
    }

    private static boolean isTrustzoneCompatible(Map<String, String> requirements) {
        return isCompatible(SystemProperties.get(PROP_TRUSTZONE),
                requirements.get(PROP_BUILD_TRUSTZONE));
    }

    public static boolean isUpdateCompatible(Map<String, String> requirements) {
        return isBootloaderCompatible(requirements)
                && isModemCompatible(requirements)
                && isBasebandCompatible(requirements)
                && isTrustzoneCompatible(requirements);
    }

    public static AlertDialog.Builder getDialog(Context context, Map<String, String> requirements) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.requirements_dialog, null);

        if (!isBootloaderCompatible(requirements)) {
            dialogView.findViewById(R.id.requirements_bootloader).setVisibility(View.VISIBLE);
            TextView list = (TextView) dialogView.findViewById(R.id.requirements_bootloader_list);
            String required = requirements.get(PROP_BUILD_BOOTLOADER)
                    .replace(REQUIREMENTS_SEPARATOR, "\n");
            list.setText(required);
        }

        if (!isModemCompatible(requirements)) {
            dialogView.findViewById(R.id.requirements_modem).setVisibility(View.VISIBLE);
            TextView list = (TextView) dialogView.findViewById(R.id.requirements_modem_list);
            String required = requirements.get(PROP_BUILD_MODEM)
                    .replace(REQUIREMENTS_SEPARATOR, "\n");
            list.setText(required);
        }

        if (!isBasebandCompatible(requirements)) {
            dialogView.findViewById(R.id.requirements_baseband).setVisibility(View.VISIBLE);
            TextView list = (TextView) dialogView.findViewById(R.id.requirements_baseband_list);
            String required = requirements.get(PROP_BUILD_BASEBAND)
                    .replace(REQUIREMENTS_SEPARATOR, "\n");
            list.setText(required);
        }

        if (!isTrustzoneCompatible(requirements)) {
            dialogView.findViewById(R.id.requirements_trustzone).setVisibility(View.VISIBLE);
            TextView list = (TextView) dialogView.findViewById(R.id.requirements_trustzone_list);
            String required = requirements.get(PROP_BUILD_TRUSTZONE)
                    .replace(REQUIREMENTS_SEPARATOR, "\n");
            list.setText(required);
        }

        return new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(R.string.requirements_title)
                .setPositiveButton(android.R.string.ok, null);
    }
}
