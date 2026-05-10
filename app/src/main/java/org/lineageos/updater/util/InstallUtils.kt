/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import java.io.File

object InstallUtils {
    @JvmStatic
    fun isScratchMounted() = runCatching {
        File("/proc/mounts").useLines { lines ->
            lines.any { it.split(" ")[1] == "/mnt/scratch" }
        }
    }.getOrDefault(false)
}
