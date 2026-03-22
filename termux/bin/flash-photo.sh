#!/data/data/com.termux/files/usr/bin/bash
# SUPERSEDED by flash-photo-broadcast.sh
#
# FAILURE MODES:
# 1. termux-camera-photo uses Camera2 API which kills the torch LED when
#    opening a camera session – they share the same hardware and the session
#    takes exclusive control. Result: 0.01–0.4% brightness (essentially black).
# 2. Bringing Termux to foreground ("am start -n com.termux/...") helps the
#    torch stay on slightly longer on EMUI, but Camera2 still kills it before
#    the shutter fires.
# 3. The root cause is in Termux:API's CameraPhotoAPI.java which hardcodes
#    CONTROL_AE_MODE_ON (mode 1), never using CONTROL_AE_MODE_ON_ALWAYS_FLASH.
#
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
