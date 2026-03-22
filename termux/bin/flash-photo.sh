#!/data/data/com.termux/files/usr/bin/bash
# Take a photo with flash
# Usage: flash-photo.sh [output_path]
# Note: On EMUI/Android 7, torch only works when Termux is in foreground

OUT="${1:-$HOME/photo_$(date +%Y%m%d_%H%M%S).jpg}"
OUTDIR=$(dirname "$OUT")
mkdir -p "$OUTDIR"

# Bring Termux to foreground (required for torch on EMUI)
am start -n com.termux/.app.TermuxActivity >/dev/null 2>&1
sleep 1

# Turn on flash, wait for it to stabilize
termux-torch on
#sleep 1

# Take photo
termux-camera-photo -c 0 "$OUT"

# Turn off flash
sleep 5
termux-torch off

# Calculate average brightness using ImageMagick (percentage 0-100)
if command -v magick >/dev/null 2>&1 && [ -s "$OUT" ]; then
    MEAN=$(magick "$OUT" -colorspace Gray -format '%[mean]' info: 2>/dev/null)
    if [ -n "$MEAN" ]; then
        # Convert from 0-65535 to 0-100%
        B=$(echo "scale=1; $MEAN / 655.35" | bc 2>/dev/null)
        [ -z "$B" ] && B="N/A"
    else
        B="N/A"
    fi
else
    B="N/A"
fi

# Report result
if [ -s "$OUT" ]; then
    SIZE=$(du -h "$OUT" | cut -f1)
    echo "$(date): Saved $OUT ($SIZE, brightness: ${B}%)"
else
    echo "$(date): ERROR - Photo failed or empty: $OUT"
fi
