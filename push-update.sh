#!/bin/sh

if [ ! -f "$1" ]; then
   echo "Usage: $0 ZIP [UNVERIFIED]"
   echo "The name of the zip is assumed to have lineage-VERSION-DATE-TYPE-*.zip as format"
   exit
fi
ZIP_PATH=`realpath $1`

adb wait-for-device
adb root

ZIP_PATH_DEVICE=/data/lineageos_updates/`basename $1`
if adb shell test -f "$ZIP_PATH_DEVICE"; then
    echo "$ZIP_PATH_DEVICE exists already"
    exit 1
fi

if [ -n "$2" ]; then
    STATUS=1
else
    STATUS=2
fi

# Assume lineage-VERSION-DATE-TYPE-*.zip
ZIP_NAME=`basename "$ZIP_PATH"`
ID=`echo "$ZIP_NAME" | sha1sum | cut -d' ' -f1`
VERSION=`echo "$ZIP_NAME" | cut -d'-' -f2`
TYPE=`echo "$ZIP_NAME" | cut -d'-' -f4`
TIMESTAMP=$((`date --date="$(echo "$ZIP_NAME" | cut -d'-' -f3)" +%s` + 86399))
SIZE=`stat -c "%s" "$ZIP_PATH"`

adb push "$ZIP_PATH" "$ZIP_PATH_DEVICE"
adb shell chgrp cache "$ZIP_PATH_DEVICE"
adb shell chmod 664 "$ZIP_PATH_DEVICE"

# Kill the app before updating the database
adb shell "killall org.lineageos.updater 2>/dev/null"
adb shell "sqlite3 /data/data/org.lineageos.updater/databases/updates.db" \
    "\"INSERT INTO updates (status, path, download_id, timestamp, type, version, size)" \
    "  VALUES ($STATUS, '$ZIP_PATH_DEVICE', '$ID', $TIMESTAMP, '$TYPE', '$VERSION', $SIZE)\""
