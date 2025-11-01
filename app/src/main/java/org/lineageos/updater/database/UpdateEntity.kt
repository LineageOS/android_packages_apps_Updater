/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/* Defines the table structure for storing update information. */

@Entity(tableName = "updates")
data class UpdateEntity(
    @ColumnInfo(name = "download_id")
    val downloadId: String,

    @ColumnInfo(name = "size")
    val fileSize: Long?,

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "path")
    val path: String?,

    @ColumnInfo(name = "status")
    val persistentStatus: Int?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "version")
    val version: String?
)