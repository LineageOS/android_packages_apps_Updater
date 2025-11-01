/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/* Provides methods to insert, query, update, and delete updates. */

@Dao
interface UpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addUpdate(update: UpdateEntity)

    @Query("UPDATE updates SET status = :status WHERE download_id = :downloadId")
    fun changeUpdateStatus(downloadId: String, status: Int)

    @Query("SELECT * FROM updates ORDER BY timestamp DESC")
    fun getUpdates(): List<UpdateEntity>

    @Query("DELETE FROM updates WHERE download_id = :downloadId")
    fun removeUpdate(downloadId: String)
}
