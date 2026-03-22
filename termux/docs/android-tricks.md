# Android Tricks Vault

## ADB Remote Access via SSH
- Connect to phone via `ssh agent@gogo "adb shell <cmd>"` (gogo has USB-connected phone)
- `adb shell input keyevent 26` — wake/sleep screen (power button)
- `adb shell input swipe 540 1800 540 200 200` — swipe up to unlock (EMUI magazine lock)
- `adb shell input text 'word1%sword2'` — type text (`%s` = space). UNRELIABLE in terminal apps!
- `adb shell input keyevent 66` — press Enter
- `adb shell input tap X Y` — tap coordinates
- `adb shell uiautomator dump /sdcard/ui.xml` — dump UI hierarchy (find elements, bounds, text)
- `adb shell screencap -p /sdcard/screen.png` — screenshot (needs screen ON for non-black)

## UI Inspection
- Parse UI dumps with Python `xml.etree.ElementTree` to find buttons, text, bounds
- Termux terminal content NOT visible in UI dumps (custom view)
- Notifications show in lock screen UI dump (useful to check app state like "0 sessions" / "1 session")

## Lock Screen
- `adb shell dumpsys window | grep mShowingLockscreen` — check lock state
- EMUI magazine lock screen: swipe up from bottom to PIN/unlock
- PIN entry: `adb shell input text '1234'` then `adb shell input keyevent 66`
- Disable PIN: done in Settings by user, then swipe-only unlock works via ADB
- `adb shell settings put secure lockscreen.disabled 1` — doesn't work on EMUI

## App Management
- `adb install /path/to/app.apk` — install APK
- `adb uninstall com.package.name` — remove app
- `adb shell pm grant com.termux.api android.permission.CAMERA` — grant permissions without UI
- `adb shell monkey -p com.termux -c android.intent.category.LAUNCHER 1` — launch app
- `adb shell am start -n com.termux/.app.TermuxActivity` — launch specific activity
- `adb shell am force-stop com.termux` — kill app

## Termux on Android 7 (Honor 5C / NEM-L21)
- Termux F-Droid APK (v1022, 109MB) works on Android 7.0+
- First launch: must be in foreground, downloads bootstrap packages
- `termux-setup-storage` — grant storage permission, creates `~/storage/shared/` → `/sdcard/`
- Termux can't access `/sdcard/` without running `termux-setup-storage` first!
- `run-as com.termux` doesn't work (not debuggable)
- Can't write to `/data/data/com.termux/files/home/` from ADB shell (different user)
- Workaround: write to `/sdcard/`, then copy from within Termux
- `$PREFIX` = `/data/data/com.termux/files/usr`
- `$TMPDIR` = `/data/data/com.termux/files/usr/tmp`
- `/tmp` does NOT exist in Termux! Use `$TMPDIR`

## Dropbear SSH in Termux
- `pkg install dropbear` — lightweight SSH server
- `dropbear -s` — start with password auth DISABLED (key-only!)
- Default port: 8022
- Username: `u0_aXXX` (check `adb shell dumpsys package com.termux | grep userId`, uid 10133 → `u0_a133`)
- Keys go in `~/.ssh/authorized_keys` (standard)
- `pkg install openssh-sftp-server` — needed for `scp` to work!

## Camera via Termux
- `pkg install termux-api` + install Termux:API APK from F-Droid
- `termux-camera-info` — list cameras, resolutions, AE modes
- `termux-camera-photo -c 0 output.jpg` — take photo (0=back, 1=front)
- **Screen must be awake** or photo is 0 bytes!
- No flash flag in `termux-camera-photo`
- **Flash workaround:** `termux-torch on &` in background, then take photo, then `termux-torch off`
- Saved as `~/bin/flash-photo.sh` on honor

## Tailscale on Android 7 (no Play Store / F-Droid version)
- Tailscale Android app requires 8.0+ — won't work on Android 7
- Install via static Linux binary in Termux:
  ```
  curl -sL "https://pkgs.tailscale.com/stable/tailscale_latest_arm64.tgz" -o $TMPDIR/tailscale.tgz
  tar xzf $TMPDIR/tailscale.tgz -C $TMPDIR/
  cp $TMPDIR/tailscale_*/tailscale $TMPDIR/tailscale_*/tailscaled $PREFIX/bin/
  ```
- Needs `--tun=userspace-networking` without root

## Device Info Commands
- `getprop ro.product.model` — model (NEM-L21)
- `getprop ro.config.marketing_name` — marketing name (Honor 7 Lite)
- `getprop net.hostname` — hostname
- `getprop ro.build.version.emui` — EMUI version
- `getprop ro.build.version.release` — Android version

## Renaming Device
```
adb shell settings put global device_name honor
adb shell setprop net.hostname honor
adb shell settings put secure bluetooth_name honor
```

## Honor 5C (NEM-L21) Specifics
- **SoC:** Kirin 650 (hi6250), 8-core ARM Cortex-A53
- **RAM:** ~1.7 GB
- **Storage:** 11 GB /data, tight on space
- **Display:** 1080×1920, 480 dpi
- **Android:** 7.0, EMUI 5.0.4, security patch 2018-08-01
- **ROM:** Stock Huawei, no viable custom ROM path (LineageOS 14.1 abandoned, bootloader unlock needs DC-Unlocker ~€4)
- **Not rooted** despite boot warning (unlocked bootloader only)
- **logcat limited** on Huawei: `open '/dev/hwlog_switch' fail` — only main and events logs
