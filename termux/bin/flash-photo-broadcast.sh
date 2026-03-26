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
# Manual exposure: ISO 800 + 100ms works for the dark utility room.
# Auto-exposure fails because AE sees mostly darkness and underexposes.
# Manual focus at 1.3 diopters (~77cm) – calibrated from AF-lock tests.
# Eliminates AF hunting which caused blurry photos.
am broadcast -n com.flashphoto/.FlashReceiver -a com.flashphoto.TAKE \
  -e file "$SHARED_OUT" --ei iso 800 --ei exposure_ms 100 \
  --ef focus_diopters 1.3 >/dev/null 2>&1

# Wait for capture to complete (camera open + 2s torch + skip frames + save)
sleep 8

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
B=$(python3 -c "
from PIL import Image
im = Image.open('$OUT').convert('L').resize((1,1))
print(f'{im.getpixel((0,0))/2.55:.1f}')
" 2>/dev/null || echo "N/A")
echo "$(date): Saved $OUT ($SIZE, brightness: ${B}%)"

# Clean up shared storage copy
rm -f "$SHARED_OUT"
