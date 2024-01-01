/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.model;

import java.io.File;

public interface UpdateInfo extends UpdateBaseInfo {
    UpdateStatus getStatus();

    int getPersistentStatus();

    File getFile();

    long getFileSize();

    int getProgress();

    long getEta();

    long getSpeed();

    int getInstallProgress();

    boolean getAvailableOnline();

    boolean getFinalizing();
}
