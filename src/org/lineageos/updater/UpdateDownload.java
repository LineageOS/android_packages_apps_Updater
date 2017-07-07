/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater;

import java.io.File;

public class UpdateDownload extends Update {

    private UpdateStatus mStatus = UpdateStatus.UNKNOWN;
    private int mPersistentStatus = UpdateStatus.Persistent.UNKNOWN;
    private File mFile;
    private long mFileSize;
    private int mProgress;
    private long mEta;
    private long mSpeed;
    private int mInstallProgress;
    private boolean mAvailableOnline;

    public UpdateStatus getStatus() {
        return mStatus;
    }

    public void setStatus(UpdateStatus status) {
        mStatus = status;
    }

    public int getPersistentStatus() {
        return mPersistentStatus;
    }

    public void setPersistentStatus(int status) {
        mPersistentStatus = status;
    }

    public File getFile() {
        return mFile;
    }

    public void setFile(File file) {
        mFile = file;
    }

    public long getFileSize() {
        return mFileSize;
    }

    public void setFileSize(long fileSize) {
        mFileSize = fileSize;
    }

    public int getProgress() {
        return mProgress;
    }

    public void setProgress(int progress) {
        mProgress = progress;
    }

    public long getEta() {
        return mEta;
    }

    public void setEta(long eta) {
        mEta = eta;
    }

    public long getSpeed() {
        return mSpeed;
    }

    public void setSpeed(long speed) {
        mSpeed = speed;
    }

    public int getInstallProgress() {
        return mInstallProgress;
    }

    public void setInstallProgress(int progress) {
        mInstallProgress = progress;
    }

    public boolean getAvailableOnline() {
        return mAvailableOnline;
    }

    public void setAvailableOnline(boolean availableOnline) {
        mAvailableOnline = availableOnline;
    }
}
