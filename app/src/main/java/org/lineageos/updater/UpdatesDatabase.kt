/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import org.lineageos.updater.model.Update
import java.io.File

/* Defines the Room database, DAO, entity, and conversion methods. */

@Database(
    entities = [UpdateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UpdatesDatabase : RoomDatabase() {

    abstract fun updateDao(): UpdateDao

    companion object {
        const val DATABASE_NAME = "updates.db"

        private var INSTANCE: UpdatesDatabase? = null

        fun getInstance(context: Context): UpdatesDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UpdatesDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Converts an Update model to UpdateEntity for database operations.
         */
        @JvmStatic
        fun toEntity(update: Update) = UpdateEntity(
            downloadId = update.downloadId,
            fileSize = update.fileSize,
            path = update.file?.absolutePath,
            persistentStatus = update.persistentStatus,
            timestamp = update.timestamp,
            type = update.type,
            version = update.version
        )

        /**
         * Converts an UpdateEntity from database to Update model.
         */
        @JvmStatic
        fun toModel(entity: UpdateEntity) = Update().apply {
            downloadId = entity.downloadId
            file = entity.path?.let { File(it) }
            fileSize = entity.fileSize ?: 0
            name = file?.name
            persistentStatus = entity.persistentStatus ?: 0
            timestamp = entity.timestamp ?: 0
            type = entity.type
            version = entity.version
        }
    }
}

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

@Entity(tableName = "updates")
data class UpdateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "download_id")
    val downloadId: String,

    @ColumnInfo(name = "size")
    val fileSize: Long?,

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