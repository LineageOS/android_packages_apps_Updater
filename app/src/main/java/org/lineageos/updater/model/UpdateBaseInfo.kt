/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.model

interface UpdateBaseInfo {
    var name: String
    var downloadId: String
    var timestamp: Long
    var type: String
    var version: String
    var downloadUrl: String
    var fileSize: Long
}
