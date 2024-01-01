/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import org.lineageos.updater.model.Update;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UpdatesDbHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "updates.db";

    public static class UpdateEntry implements BaseColumns {
        public static final String TABLE_NAME = "updates";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_PATH = "path";
        public static final String COLUMN_NAME_DOWNLOAD_ID = "download_id";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
        public static final String COLUMN_NAME_TYPE = "type";
        public static final String COLUMN_NAME_VERSION = "version";
        public static final String COLUMN_NAME_SIZE = "size";
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + UpdateEntry.TABLE_NAME + " (" +
                    UpdateEntry._ID + " INTEGER PRIMARY KEY," +
                    UpdateEntry.COLUMN_NAME_STATUS + " INTEGER," +
                    UpdateEntry.COLUMN_NAME_PATH + " TEXT," +
                    UpdateEntry.COLUMN_NAME_DOWNLOAD_ID + " TEXT NOT NULL UNIQUE," +
                    UpdateEntry.COLUMN_NAME_TIMESTAMP + " INTEGER," +
                    UpdateEntry.COLUMN_NAME_TYPE + " TEXT," +
                    UpdateEntry.COLUMN_NAME_VERSION + " TEXT," +
                    UpdateEntry.COLUMN_NAME_SIZE + " INTEGER)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + UpdateEntry.TABLE_NAME;

    public UpdatesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void addUpdateWithOnConflict(Update update, int conflictAlgorithm) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        fillContentValues(update, values);
        db.insertWithOnConflict(UpdateEntry.TABLE_NAME, null, values, conflictAlgorithm);
    }

    private static void fillContentValues(Update update, ContentValues values) {
        values.put(UpdateEntry.COLUMN_NAME_STATUS, update.getPersistentStatus());
        values.put(UpdateEntry.COLUMN_NAME_PATH, update.getFile().getAbsolutePath());
        values.put(UpdateEntry.COLUMN_NAME_DOWNLOAD_ID, update.getDownloadId());
        values.put(UpdateEntry.COLUMN_NAME_TIMESTAMP, update.getTimestamp());
        values.put(UpdateEntry.COLUMN_NAME_TYPE, update.getType());
        values.put(UpdateEntry.COLUMN_NAME_VERSION, update.getVersion());
        values.put(UpdateEntry.COLUMN_NAME_SIZE, update.getFileSize());
    }

    public void removeUpdate(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        String selection = UpdateEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] selectionArgs = {downloadId};
        db.delete(UpdateEntry.TABLE_NAME, selection, selectionArgs);
    }

    public void changeUpdateStatus(Update update) {
        String selection = UpdateEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] selectionArgs = {update.getDownloadId()};
        changeUpdateStatus(selection, selectionArgs, update.getPersistentStatus());
    }

    private void changeUpdateStatus(String selection, String[] selectionArgs,
                                    int status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(UpdateEntry.COLUMN_NAME_STATUS, status);
        db.update(UpdateEntry.TABLE_NAME, values, selection, selectionArgs);
    }

    public List<Update> getUpdates() {
        return getUpdates(null, null);
    }

    public List<Update> getUpdates(String selection, String[] selectionArgs) {
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = {
                UpdateEntry.COLUMN_NAME_PATH,
                UpdateEntry.COLUMN_NAME_DOWNLOAD_ID,
                UpdateEntry.COLUMN_NAME_TIMESTAMP,
                UpdateEntry.COLUMN_NAME_TYPE,
                UpdateEntry.COLUMN_NAME_VERSION,
                UpdateEntry.COLUMN_NAME_STATUS,
                UpdateEntry.COLUMN_NAME_SIZE,
        };
        String sort = UpdateEntry.COLUMN_NAME_TIMESTAMP + " DESC";
        Cursor cursor = db.query(UpdateEntry.TABLE_NAME, projection, selection, selectionArgs,
                null, null, sort);
        List<Update> updates = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Update update = new Update();
                int index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_PATH);
                update.setFile(new File(cursor.getString(index)));
                update.setName(update.getFile().getName());
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_DOWNLOAD_ID);
                update.setDownloadId(cursor.getString(index));
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_TIMESTAMP);
                update.setTimestamp(cursor.getLong(index));
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_TYPE);
                update.setType(cursor.getString(index));
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_VERSION);
                update.setVersion(cursor.getString(index));
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_STATUS);
                update.setPersistentStatus(cursor.getInt(index));
                index = cursor.getColumnIndex(UpdateEntry.COLUMN_NAME_SIZE);
                update.setFileSize(cursor.getLong(index));
                updates.add(update);
            }
            cursor.close();
        }
        return updates;
    }
}
