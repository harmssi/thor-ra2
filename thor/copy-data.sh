#!/bin/bash
# Copy your Red Alert 2 / Yuri's Revenge game files into Thor RA2's data folder over adb.
#
#   thor/copy-data.sh                       # from the upstream RA2 Engine app (org.ra2.engine)
#   SRC=/sdcard/Download/RA2 thor/copy-data.sh
#
# Only .mix and .csf files are copied; the source folder is only read. "adb push" cannot
# create new files in another app's Android/data on Android 11+, so the files are streamed
# with "adb shell cat" (the shell user may write into the app's own files folder).
set -euo pipefail
SRC=${SRC:-/sdcard/Android/data/org.ra2.engine/files/ra2}
DEST=/sdcard/Android/data/de.harmssi.thorra2/files/ra2

adb shell "mkdir -p $DEST"
for f in $(adb shell "cd $SRC && ls" | tr -d '\r'); do
    case "$(echo "$f" | tr 'A-Z' 'a-z')" in
        *.mix|*.csf) ;;
        *) continue ;;
    esac
    size=$(adb shell "stat -c %s '$SRC/$f'" | tr -d '\r')
    have=$(adb shell "stat -c %s '$DEST/$f' 2>/dev/null || echo -1" | tr -d '\r')
    if [ "$size" = "$have" ]; then
        echo "  $f (already there)"
        continue
    fi
    echo "  $f ($size bytes)"
    adb shell "cat '$SRC/$f' > '$DEST/$f' && chmod 666 '$DEST/$f'"
done
echo "Done: $DEST"
