#!/data/data/com.termux/files/usr/bin/bash
# flash-photo-broadcast.sh - Take a flash photo via FlashPhoto APK broadcast
#
# Uses the FlashPhoto app (com.flashphoto) with Camera2 API + TORCH mode.
# Triggered via "am broadcast" from Termux - NO ADB required, NO USB required!
#
# REQUIREMENTS:
# - FlashPhoto APK installed (com.flashphoto) with camera+storage permissions
# - That is all. No USB cable, no ADB, no screen wake needed.
#
# USAGE:
#   ~/bin/flash-photo-broadcast.sh                    # default path
#   ~/bin/flash-photo-broadcast.sh ~/photos/test.jpg  # custom path

set -eu

OUT="${1:-$HOME/photos/$(date +%Y-%m)/$(date +%Y%m%d_%H%M%S).jpg}"
SHARED_OUT="/sdcard/DCIM/FlashPhoto/capture_$(date +%Y%m%d_%H%M%S).jpg"

mkdir -p "$(dirname "$OUT")"

# Take photo via broadcast (FlashPhoto app handles camera+flash)
am broadcast -n com.flashphoto/.FlashReceiver -a com.flashphoto.TAKE \
  -e file "$SHARED_OUT" >/dev/null 2>&1

# Wait for capture to complete (camera open + 2s torch + capture + save)
sleep 12

# Check if photo was created
if [ ! -s "$SHARED_OUT" ]; then
    echo "$(date): ERROR - Photo not saved at $SHARED_OUT" >&2
    exit 1
fi

# Copy from shared storage to Termux-accessible path
cp "$SHARED_OUT" "$OUT"

if [ ! -s "$OUT" ]; then
    echo "$(date): ERROR - Copy failed" >&2
    exit 1
fi

# Report
SIZE=$(du -h "$OUT" | cut -f1)
B="N/A"
if command -v magick >/dev/null 2>&1; then
    B=$(magick "$OUT" -colorspace Gray -format "%[fx:mean*100]" info: 2>/dev/null || echo "N/A")
fi
echo "$(date): Saved $OUT ($SIZE, brightness: ${B}%)"

# Clean up shared storage copy
rm -f "$SHARED_OUT"
