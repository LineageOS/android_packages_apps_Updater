/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.model;

public interface UpdateBaseInfo {
    String getName();

    String getDownloadId();

    long getTimestamp();

    String getType();

    String getVersion();

    String getDownloadUrl();

    long getFileSize();
}
