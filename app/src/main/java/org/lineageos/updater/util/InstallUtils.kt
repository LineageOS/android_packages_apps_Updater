/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import java.io.File
import java.io.IOException

object InstallUtils {

    @JvmStatic
    fun isScratchMounted(): Boolean {
        return try {
            File("/proc/mounts").useLines { lines ->
                lines.any { it.split(" ")[1] == "/mnt/scratch" }
            }
        } catch (_: IOException) {
            false
        }
    }
}
