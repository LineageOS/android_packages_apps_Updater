/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.model;

import java.io.File;

public class Update extends UpdateBase implements UpdateInfo {
    public static final String LOCAL_ID = "local";

    private UpdateStatus mStatus = UpdateStatus.UNKNOWN;
    private int mPersistentStatus = UpdateStatus.Persistent.UNKNOWN;
    private File mFile;
    private int mProgress;
    private long mEta;
    private long mSpeed;
    private int mInstallProgress;
    private boolean mAvailableOnline;
    private boolean mIsFinalizing;

    public Update() {
    }

    public Update(UpdateInfo update) {
        super(update);
        mStatus = update.getStatus();
        mPersistentStatus = update.getPersistentStatus();
        mFile = update.getFile();
        mProgress = update.getProgress();
        mEta = update.getEta();
        mSpeed = update.getSpeed();
        mInstallProgress = update.getInstallProgress();
        mAvailableOnline = update.getAvailableOnline();
        mIsFinalizing = update.getFinalizing();
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

    @Override
    public boolean getFinalizing() {
        return mIsFinalizing;
    }

    public void setFinalizing(boolean finalizing) {
        mIsFinalizing = finalizing;
    }
}
