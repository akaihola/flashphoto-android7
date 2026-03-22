#!/data/data/com.termux/files/usr/bin/bash
# SUPERSEDED by flash-photo-broadcast.sh
#
# DRAWBACKS:
# 1. Requires USB cable to be physically connected at all times. On this EMUI
#    device, adbd unconditionally stops when USB is disconnected, even with TCP
#    mode active and a TCP client connected. This is EMUI-specific – stock
#    Android keeps adbd alive in TCP mode.
# 2. Requires "adb tcpip 5555" after every reboot (from a computer with USB).
# 3. Screen must be awake for "input swipe" to register – adds complexity
#    (wake check, unlock swipe) and risk (screen-off kills Wi-Fi on EMUI).
# 4. Produces excellent results (27–44% brightness) but the USB requirement
#    makes it unsuitable for fully autonomous deployment.
#
# flash-photo-adb-local.sh - Take a flash photo via self-loopback ADB
#
# Uses Huawei camera app with flash set to "On" (Päälle).
# Input injection via local ADB (Termux adb → emulator-5554).
# No USB cable needed at runtime (but USB must be connected for adbd to run
# on this EMUI device – adbd stops when USB disconnects).
#
# REQUIREMENTS:
# - USB cable connected (keeps adbd alive on EMUI)
# - ADB TCP mode enabled (adb tcpip 5555, after each reboot)
# - Flash mode set to "Päälle" (On) in Huawei camera app
# - android-tools package installed in Termux
# - ADB key pre-authorized (no auth dialog on connect)
#
# NOTE: Do NOT turn screen off at the end – it may disable Wi-Fi and kill SSH.

set -eu

ADB="adb"
ADB_TARGET="emulator-5554"
OUT="${1:-$HOME/photos/$(date +%Y-%m)/$(date +%Y%m%d_%H%M%S).jpg}"
DCIM_DIR="/storage/6464-3532/DCIM/Camera"

mkdir -p "$(dirname "$OUT")"

# Ensure ADB server is running (don't kill existing one)
if ! $ADB devices 2>/dev/null | grep -q "$ADB_TARGET"; then
    $ADB start-server >/dev/null 2>&1 || true
    sleep 2
    $ADB connect 127.0.0.1:5555 >/dev/null 2>&1 || true
    sleep 1
fi

# Verify connection
if ! $ADB devices 2>/dev/null | grep -q "$ADB_TARGET.*device"; then
    echo "$(date): ERROR - Cannot connect to local ADB (is USB connected? is TCP mode enabled?)" >&2
    exit 1
fi

# Wake screen if asleep (check first to avoid toggling OFF)
WAKE=$($ADB -s "$ADB_TARGET" shell 'dumpsys power | grep mWakefulness' 2>/dev/null || true)
if echo "$WAKE" | grep -q "Asleep"; then
    $ADB -s "$ADB_TARGET" shell 'input keyevent 26' 2>/dev/null  # POWER (wake)
    sleep 1
fi
# Swipe to unlock (harmless if already unlocked)
$ADB -s "$ADB_TARGET" shell 'input swipe 540 1600 540 400 300' 2>/dev/null
sleep 2

# Record existing latest photo
BEFORE_LATEST=$(ls -1t "$DCIM_DIR"/IMG_*.jpg 2>/dev/null | head -1 || true)

# Launch Huawei camera
am start -a android.media.action.STILL_IMAGE_CAMERA >/dev/null 2>&1
sleep 3

# Press shutter via ADB input injection
$ADB -s "$ADB_TARGET" shell 'input swipe 540 1677 540 1677 150' 2>/dev/null
sleep 5

# Find newest photo
LATEST=$(ls -1t "$DCIM_DIR"/IMG_*.jpg 2>/dev/null | head -1 || true)

if [ -z "$LATEST" ]; then
    echo "$(date): ERROR - No photos found in $DCIM_DIR" >&2
    exit 1
fi

if [ "$LATEST" = "$BEFORE_LATEST" ]; then
    echo "$(date): WARNING - No new photo detected (shutter may have failed)" >&2
    exit 1
fi

# Copy to output
cp "$LATEST" "$OUT"

if [ ! -s "$OUT" ]; then
    echo "$(date): ERROR - Output file empty" >&2
    exit 1
fi

# Report with brightness
SIZE=$(du -h "$OUT" | cut -f1)
B="N/A"
if command -v magick >/dev/null 2>&1; then
    B=$(magick "$OUT" -colorspace Gray -format '%[fx:mean*100]' info: 2>/dev/null || echo "N/A")
fi
echo "$(date): Saved $OUT ($SIZE, brightness: ${B}%)"
