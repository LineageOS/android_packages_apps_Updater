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
package org.lineageos.updater.model;

import java.io.File;

public class Update extends UpdateBase implements UpdateInfo {

    private UpdateStatus mStatus = UpdateStatus.UNKNOWN;
    private int mPersistentStatus = UpdateStatus.Persistent.UNKNOWN;
    private File mFile;
    private long mFileSize;
    private int mProgress;
    private long mEta;
    private long mSpeed;
    private int mInstallProgress;
    private boolean mAvailableOnline;

    public Update() {
    }

    public Update(UpdateBaseInfo update) {
        super(update);
    }

    public Update(UpdateInfo update) {
        super(update);
        mStatus = update.getStatus();
        mPersistentStatus = update.getPersistentStatus();
        mFile = update.getFile();
        mFileSize = update.getFileSize();
        mProgress = update.getProgress();
        mEta = update.getEta();
        mSpeed = update.getSpeed();
        mInstallProgress = update.getInstallProgress();
        mAvailableOnline = update.getAvailableOnline();
    }

    @Override
    public UpdateStatus getStatus() {
        return mStatus;
    }

    public void setStatus(UpdateStatus status) {
        mStatus = status;
    }

    @Override
    public int getPersistentStatus() {
        return mPersistentStatus;
    }

    public void setPersistentStatus(int status) {
        mPersistentStatus = status;
    }

    @Override
    public File getFile() {
        return mFile;
    }

    public void setFile(File file) {
        mFile = file;
    }

    @Override
    public long getFileSize() {
        return mFileSize;
    }

    public void setFileSize(long fileSize) {
        mFileSize = fileSize;
    }

    @Override
    public int getProgress() {
        return mProgress;
    }

    public void setProgress(int progress) {
        mProgress = progress;
    }

    @Override
    public long getEta() {
        return mEta;
    }

    public void setEta(long eta) {
        mEta = eta;
    }

    @Override
    public long getSpeed() {
        return mSpeed;
    }

    public void setSpeed(long speed) {
        mSpeed = speed;
    }

    @Override
    public int getInstallProgress() {
        return mInstallProgress;
    }

    public void setInstallProgress(int progress) {
        mInstallProgress = progress;
    }

    @Override
    public boolean getAvailableOnline() {
        return mAvailableOnline;
    }

    public void setAvailableOnline(boolean availableOnline) {
        mAvailableOnline = availableOnline;
    }
}
