#!/bin/sh

# SPDX-FileCopyrightText: 2018-2020 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0

updates_dir=/data/lineageos_updates

if [ ! -f "$1" ]; then
   echo "Usage: $0 ZIP [UNVERIFIED]"
   echo "Push ZIP to $updates_dir and add it to Updater"
   echo
   echo "The name of ZIP is assumed to have lineage-VERSION-DATE-TYPE-* as format"
   echo "If UNVERIFIED is set, the app will verify the update"
   exit
fi
zip_path=`realpath "$1"`

if [ "`adb get-state 2>/dev/null`" != "device" ]; then
    echo "No device found. Waiting for one..."
    adb wait-for-device
fi
if ! adb root; then
    echo "Could not run adbd as root"
    exit 1
fi

zip_path_device=$updates_dir/`basename "$zip_path"`
if adb shell test -f "$zip_path_device"; then
    echo "$zip_path_device exists already"
    exit 1
fi

if [ -n "$2" ]; then
    status=1
else
    status=2
fi

# Assume lineage-VERSION-DATE-TYPE-*.zip
zip_name=`basename "$zip_path"`
id=`echo "$zip_name" | sha1sum | cut -d' ' -f1`
version=`echo "$zip_name" | cut -d'-' -f2`
type=`echo "$zip_name" | cut -d'-' -f4`
build_date=`echo "$zip_name" | cut -d'-' -f3 | cut -d'_' -f1`
if [ "`uname`" = "Darwin" ]; then
    timestamp=`date -jf "%Y%m%d %H:%M:%S" "$build_date 23:59:59" +%s`
    size=`stat -f%z "$zip_path"`
else
    timestamp=`date --date="$build_date 23:59:59" +%s`
    size=`stat -c "%s" "$zip_path"`
fi

adb push "$zip_path" "$zip_path_device"
adb shell chgrp cache "$zip_path_device"
adb shell chmod 664 "$zip_path_device"

# Kill the app before updating the database
adb shell "killall org.lineageos.updater 2>/dev/null"
adb shell "sqlite3 /data/data/org.lineageos.updater/databases/updates.db" \
    "\"INSERT INTO updates (status, path, download_id, timestamp, type, version, size)" \
    "  VALUES ($status, '$zip_path_device', '$id', $timestamp, '$type', '$version', $size)\""

# Exit root mode
adb unroot
