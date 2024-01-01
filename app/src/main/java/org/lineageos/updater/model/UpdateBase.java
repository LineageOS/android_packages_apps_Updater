/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.model;

public class UpdateBase implements UpdateBaseInfo {

    private String mName;
    private String mDownloadUrl;
    private String mDownloadId;
    private long mTimestamp;
    private String mType;
    private String mVersion;
    private long mFileSize;

    public UpdateBase() {
    }

    public UpdateBase(UpdateBaseInfo update) {
        mName = update.getName();
        mDownloadUrl = update.getDownloadUrl();
        mDownloadId = update.getDownloadId();
        mTimestamp = update.getTimestamp();
        mType = update.getType();
        mVersion = update.getVersion();
        mFileSize = update.getFileSize();
    }

    @Override
    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    @Override
    public String getDownloadId() {
        return mDownloadId;
    }

    public void setDownloadId(String downloadId) {
        mDownloadId = downloadId;
    }

    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    @Override
    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    @Override
    public String getVersion() {
        return mVersion;
    }

    public void setVersion(String version) {
        mVersion = version;
    }

    @Override
    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        mDownloadUrl = downloadUrl;
    }

    @Override
    public long getFileSize() {
        return mFileSize;
    }

    public void setFileSize(long fileSize) {
        mFileSize = fileSize;
    }
}
