/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the updates table.
 */
@Dao
interface UpdateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(update: UpdateEntity)

    @Query("DELETE FROM updates WHERE download_id = :downloadId")
    fun delete(downloadId: String)

    @Query("UPDATE updates SET status = :status WHERE download_id = :downloadId")
    fun changeStatus(downloadId: String, status: Int)

    @Query("SELECT * FROM updates ORDER BY timestamp DESC")
    fun getUpdates(): List<UpdateEntity>

    @Query("SELECT * FROM updates ORDER BY timestamp DESC")
    fun observeUpdates(): Flow<List<UpdateEntity>>
}
