/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.lineageos.updater.model.Update
import java.io.File

/* Manages the database instance and provides model conversion utilities. */

@Database(
    entities = [UpdateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UpdatesDatabase : RoomDatabase() {

    abstract fun updateDao(): UpdateDao

    companion object {
        const val DATABASE_NAME = "updates.db"

        @Volatile
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