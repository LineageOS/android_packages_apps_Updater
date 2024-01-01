/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-FileCopyrightText: 2020-2022 SHIFT GmbH
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONException;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UpdateImporter {
    private static final int REQUEST_PICK = 9061;
    private static final String TAG = "UpdateImporter";
    private static final String MIME_ZIP = "application/zip";
    private static final String FILE_NAME = "localUpdate.zip";
    private static final String METADATA_PATH = "META-INF/com/android/metadata";
    private static final String METADATA_TIMESTAMP_KEY = "post-timestamp=";

    private final Activity activity;
    private final Callbacks callbacks;

    private Thread workingThread;

    public UpdateImporter(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    public void stopImport() {
        if (workingThread != null && workingThread.isAlive()) {
            workingThread.interrupt();
            workingThread = null;
        }
    }

    public void openImportPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MIME_ZIP);
        activity.startActivityForResult(intent, REQUEST_PICK);
    }

    public boolean onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_PICK) {
            return false;
        }

        return onPicked(data.getData());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean onPicked(Uri uri) {
        callbacks.onImportStarted();

        workingThread = new Thread(() -> {
            File importedFile = null;
            try {
                importedFile = importFile(uri);
                verifyPackage(importedFile);

                final Update update = buildLocalUpdate(importedFile);
                addUpdate(update);
                activity.runOnUiThread(() -> callbacks.onImportCompleted(update));
            } catch (Exception e) {
                Log.e(TAG, "Failed to import update package", e);
                // Do not store invalid update
                if (importedFile != null) {
                    importedFile.delete();
                }

                activity.runOnUiThread(() -> callbacks.onImportCompleted(null));
            }
        });
        workingThread.start();
        return true;
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File importFile(Uri uri) throws IOException {
        final ParcelFileDescriptor parcelDescriptor = activity.getContentResolver()
                .openFileDescriptor(uri, "r");
        if (parcelDescriptor == null) {
            throw new IOException("Failed to obtain fileDescriptor");
        }

        final FileInputStream iStream = new FileInputStream(parcelDescriptor
                .getFileDescriptor());
        final File downloadDir = Utils.getDownloadPath(activity);
        final File outFile = new File(downloadDir, FILE_NAME);
        if (outFile.exists()) {
            outFile.delete();
        }
        final FileOutputStream oStream = new FileOutputStream(outFile);

        int read;
        final byte[] buffer = new byte[4096];
        while ((read = iStream.read(buffer)) > 0) {
            oStream.write(buffer, 0, read);
        }
        oStream.flush();
        oStream.close();
        iStream.close();
        parcelDescriptor.close();

        outFile.setReadable(true, false);

        return outFile;
    }

    private Update buildLocalUpdate(File file) {
        final long timeStamp = getTimeStamp(file);
        final String buildDate = StringGenerator.getDateLocalizedUTC(
                activity, DateFormat.MEDIUM, timeStamp);
        final String name = activity.getString(R.string.local_update_name);
        final Update update = new Update();
        update.setAvailableOnline(false);
        update.setName(name);
        update.setFile(file);
        update.setFileSize(file.length());
        update.setDownloadId(Update.LOCAL_ID);
        update.setTimestamp(timeStamp);
        update.setStatus(UpdateStatus.VERIFIED);
        update.setPersistentStatus(UpdateStatus.Persistent.VERIFIED);
        update.setVersion(String.format("%s (%s)", name, buildDate));
        return update;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void verifyPackage(File file) throws Exception {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
        } catch (Exception e) {
            if (file.exists()) {
                file.delete();
                throw new Exception("Verification failed, file has been deleted");
            } else {
                throw e;
            }
        }
    }

    private void addUpdate(Update update) {
        UpdaterController controller = UpdaterController.getInstance(activity);
        controller.addUpdate(update, false);
    }

    private long getTimeStamp(File file) {
        try {
            final String metadataContent = readZippedFile(file, METADATA_PATH);
            final String[] lines = metadataContent.split("\n");
            for (String line : lines) {
                if (!line.startsWith(METADATA_TIMESTAMP_KEY)) {
                    continue;
                }

                final String timeStampStr = line.replace(METADATA_TIMESTAMP_KEY, "");
                return Long.parseLong(timeStampStr);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read date from local update zip package", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse timestamp number from zip metadata file", e);
        }

        Log.e(TAG, "Couldn't find timestamp in zip file, falling back to $now");
        return System.currentTimeMillis();
    }

    private String readZippedFile(File file, String path) throws IOException {
        final StringBuilder sb = new StringBuilder();
        InputStream iStream = null;

        try (final ZipFile zip = new ZipFile(file)) {
            final Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                final ZipEntry entry = iterator.nextElement();
                if (!METADATA_PATH.equals(entry.getName())) {
                    continue;
                }

                iStream = zip.getInputStream(entry);
                break;
            }

            if (iStream == null) {
                throw new FileNotFoundException("Couldn't find " + path + " in " + file.getName());
            }

            final byte[] buffer = new byte[1024];
            int read;
            while ((read = iStream.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file from zip package", e);
            throw e;
        } finally {
            if (iStream != null) {
                iStream.close();
            }
        }

        return sb.toString();
    }

    public interface Callbacks {
        void onImportStarted();

        void onImportCompleted(Update update);
    }
}
