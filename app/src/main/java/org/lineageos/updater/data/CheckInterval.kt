/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class CheckInterval(val duration: Duration, val storageValue: String) {
    DAILY(1.days, "daily"),
    WEEKLY(7.days, "weekly"),
    MONTHLY(30.days, "monthly");

    companion object {
        val default = WEEKLY

        fun fromStorageValue(value: String?) =
            entries.find { it.storageValue == value } ?: default
    }
}
