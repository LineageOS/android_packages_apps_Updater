/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data.source.local

import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus

class UpdatesLocalDataSource(private val updateDao: UpdateDao) {

    fun getUpdates(): List<Update> = updateDao.getUpdates().map { it.toUpdate() }

    fun addUpdate(update: Update) {
        updateDao.insertOrReplace(update.toEntity())
    }

    fun removeUpdate(downloadId: String) = updateDao.delete(downloadId)

    fun changeStatus(downloadId: String, status: UpdateStatus) =
        updateDao.changeStatus(downloadId, status.persistentStatus)
}
