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
}
