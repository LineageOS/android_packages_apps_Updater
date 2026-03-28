/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the Updater application.
 *
 * Use [UpdatesLocalDataSource] to interact with data.
 */
@Database(entities = [UpdateEntity::class], version = 2, exportSchema = true)
abstract class UpdatesDatabase : RoomDatabase() {
    abstract fun updateDao(): UpdateDao

    companion object {
        @Volatile
        private var instance: UpdatesDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `updates_new` (
                        `download_id` TEXT NOT NULL,
                        `download_url` TEXT,
                        `name` TEXT NOT NULL,
                        `path` TEXT,
                        `size` INTEGER NOT NULL,
                        `status` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT,
                        `version` TEXT NOT NULL,
                        PRIMARY KEY(`download_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `updates_new` (`download_id`, `status`, `path`,
                        `timestamp`, `type`, `version`, `size`, `name`)
                    SELECT `download_id`, IFNULL(`status`, 0), `path`,
                        IFNULL(`timestamp`, 0), `type`, IFNULL(`version`, ''), IFNULL(`size`, 0),
                        COALESCE(REPLACE(`path`, RTRIM(`path`, REPLACE(`path`, '/', '')), ''), '')
                    FROM `updates`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `updates`")
                db.execSQL("ALTER TABLE `updates_new` RENAME TO `updates`")
            }
        }

        @JvmStatic
        fun getInstance(context: Context): UpdatesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UpdatesDatabase::class.java,
                "updates.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
