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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class PermissionsUtils {

    public static boolean hasPermission(Context context, String permission) {
        int permissionState = context.checkSelfPermission(permission);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check the given permissions and requests them if needed.
     *
     * @param activity The target activity
     * @param permissions The permissions to check
     * @param requestCode @see Activity#requestPermissions(String[] , int)
     * @return true if the permission is granted, false otherwise
     */
    public static boolean checkAndRequestPermissions(final Activity activity,
            final String[] permissions, final int requestCode) {
        List<String> permissionsList = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(activity, permission)) {
                permissionsList.add(permission);
            }
        }
        if (permissionsList.size() == 0) {
            return true;
        } else {
            String[] permissionsArray = new String[permissionsList.size()];
            permissionsArray = permissionsList.toArray(permissionsArray);
            activity.requestPermissions(permissionsArray, requestCode);
            return false;
        }
    }

    /**
     * Check and request the write external storage permission
     *
     * @see #checkAndRequestPermissions(Activity, String[], int)
     */
    public static boolean checkAndRequestStoragePermission(Activity activity, int requestCode) {
        return checkAndRequestPermissions(activity,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                requestCode);
    }
}
