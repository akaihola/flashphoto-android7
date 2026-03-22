#!/data/data/com.termux/files/usr/bin/bash
# flash-photo-v2.sh - Take a photo with flash on Honor NEM-L21
#
# Uses Huawei camera app with flash set to "On" (Päälle).
# Flash mode persists across app restarts once set.
#
# REQUIREMENTS:
# - Flash mode must be set to "Päälle" (On) in camera app
#   (done once via ADB: see set-flash-on.sh)
# - ADB connected OR camera already in foreground
#
# APPROACH:
# 1. Launch Huawei camera via am start
# 2. Simulate shutter tap via ADB shell (requires USB or TCP ADB)
# 3. Copy the resulting photo from SD card DCIM folder
#
# ADB COORDINATES (1080x1920, 480dpi):
#   Flash icon: (108, 54)
#   Flash "Päälle" (On): (150, 385) 
#   Shutter button: (540, 1677) - use swipe for reliable press

OUT="${1:-$HOME/photos/$(date +%Y-%m)/$(date +%Y%m%d_%H%M%S).jpg}"
OUTDIR=$(dirname "$OUT")
mkdir -p "$OUTDIR"

DCIM_DIR="/storage/6464-3532/DCIM/Camera"

# Record what photos exist before capture
BEFORE_COUNT=$(ls "$DCIM_DIR"/IMG_*.jpg 2>/dev/null | wc -l)
BEFORE_LATEST=$(ls -t "$DCIM_DIR"/IMG_*.jpg 2>/dev/null | head -1)

# Launch camera
am start -a android.media.action.STILL_IMAGE_CAMERA >/dev/null 2>&1
sleep 2

# Simulate shutter press via ADB
# This requires ADB to be connected (USB or TCP)
# Try ADB shell first, fall back to error message
if command -v adb >/dev/null 2>&1; then
    adb shell 'input swipe 540 1677 540 1677 150' 2>/dev/null
elif [ -x /system/bin/input ]; then
    # Direct input (needs app_process in PATH - usually doesn't work from Termux)
    PATH=$PATH:/system/bin /system/bin/input swipe 540 1677 540 1677 150 2>/dev/null
fi

# Wait for photo to be saved
sleep 5

# Find the newest photo
LATEST=$(ls -t "$DCIM_DIR"/IMG_*.jpg 2>/dev/null | head -1)

if [ -z "$LATEST" ]; then
    echo "$(date): ERROR - No photos found in $DCIM_DIR" >&2
    exit 1
fi

if [ "$LATEST" = "$BEFORE_LATEST" ]; then
    echo "$(date): WARNING - No new photo detected (shutter tap may have failed)" >&2
    echo "$(date): Using most recent photo: $LATEST" >&2
fi

# Copy to output path
cp "$LATEST" "$OUT"

if [ -s "$OUT" ]; then
    SIZE=$(du -h "$OUT" | cut -f1)
    # Calculate brightness
    B="N/A"
    if command -v magick >/dev/null 2>&1; then
        MEAN=$(magick "$OUT" -colorspace Gray -format '%[mean]' info: 2>/dev/null)
        if [ -n "$MEAN" ]; then
            B=$(echo "scale=1; $MEAN / 655.35" | bc 2>/dev/null)
            [ -z "$B" ] && B="N/A"
        fi
    fi
    echo "$(date): Saved $OUT ($SIZE, brightness: ${B}%)"
else
    echo "$(date): ERROR - Output file empty or missing" >&2
    exit 1
fi
