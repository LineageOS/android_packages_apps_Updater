/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.model;

public enum UpdateStatus {
    UNKNOWN,
    STARTING,
    DOWNLOADING,
    PAUSED,
    PAUSED_ERROR,
    DELETED,
    VERIFYING,
    VERIFIED,
    VERIFICATION_FAILED,
    INSTALLING,
    INSTALLED,
    INSTALLATION_FAILED,
    INSTALLATION_CANCELLED,
    INSTALLATION_SUSPENDED;

    public static final class Persistent {
        public static final int UNKNOWN = 0;
        public static final int INCOMPLETE = 1;
        public static final int VERIFIED = 2;
    }
}
