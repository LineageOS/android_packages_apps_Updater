#!/bin/sh

if [ ! -f "$1" ]; then
   echo "Usage: $0 ZIP [UNVERIFIED]"
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

ID=`basename $1 | sha1sum | cut -d' ' -f1`
VERSION=`unzip -p "$ZIP_PATH" system/build.prop | grep '^ro.\(lineage\|cm\).build.version=' | cut -d'=' -f2`
TYPE=`unzip -p "$ZIP_PATH" system/build.prop | grep '^ro.\(lineage\|cm\).releasetype=' | cut -d'=' -f2`
TIMESTAMP=`unzip -p "$ZIP_PATH" system/build.prop | grep '^ro.build.date.utc=' | cut -d'=' -f2`
SIZE=`stat --printf="%s" "$ZIP_PATH"`

adb push "$ZIP_PATH" "$ZIP_PATH_DEVICE"
adb shell chgrp cache "$ZIP_PATH_DEVICE"
adb shell chmod 660 "$ZIP_PATH_DEVICE"

# Kill the app before updating the database
adb shell "killall org.lineageos.updater 2>/dev/null"
adb shell "sqlite3 /data/data/org.lineageos.updater/databases/updates.db" \
    "\"INSERT INTO updates (status, path, download_id, timestamp, type, version, size)" \
    "  VALUES ($STATUS, '$ZIP_PATH_DEVICE', '$ID', $TIMESTAMP, '$TYPE', '$VERSION', $SIZE)\""
