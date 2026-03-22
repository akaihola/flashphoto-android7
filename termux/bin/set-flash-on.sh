#!/data/data/com.termux/files/usr/bin/bash
# NOT NEEDED for flash-photo-broadcast.sh (FlashPhoto APK handles flash itself).
# Only needed for the legacy ADB-based methods (flash-photo-adb-local.sh,
# flash-photo-v2.sh) which rely on the Huawei camera app's flash setting.
#
# LIMITATION: Requires ADB shell context (the "input" command needs shell UID).
# Cannot be run from Termux directly – must be run via ADB.
#
# set-flash-on.sh - Set Huawei camera flash mode to "On" (Päälle)
# Run this ONCE via ADB shell, not from Termux (needs 'input' command)
# The setting persists across camera restarts.
#
# Run via ADB:
#   adb shell 'sh /data/data/com.termux/files/home/bin/set-flash-on.sh'
#
# Or manually from the device UI:
#   1. Open camera
#   2. Tap flash icon (top-left, ⚡ symbol)
#   3. Select "Päälle" (On) from dropdown

# Open camera
am start -a android.media.action.STILL_IMAGE_CAMERA
sleep 3

# Tap flash icon to open dropdown menu
# Flash button bounds: [54,0][162,108], center (108, 54)
input tap 108 54
sleep 1

# Tap "Päälle" (On) - 3rd option in dropdown
# Position found empirically: (150, 385)
input swipe 150 385 150 385 100
sleep 1

echo "Flash mode set to ON. Verify by checking the ⚡ icon (no A superscript)."
