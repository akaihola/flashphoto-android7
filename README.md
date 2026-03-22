# FlashPhoto for Android 7 (EMUI)

[![Built with Claude Code](https://img.shields.io/badge/Built_with-Claude_Code-6f42c1?logo=anthropic&logoColor=white)](https://claude.ai/code)

> This project is developed by an AI coding agent ([Claude][claude-code], via
> [Pi][pi-agent]), with human oversight and direction.

A minimal Android APK that takes flash-illuminated photos via Camera2 API,
triggered by broadcast intent from Termux. Built to work autonomously on an
Honor NEM-L21 (Android 7.0, EMUI) deployed for periodic timelapse photography –
**no USB cable, no screen wake, no human interaction required**.

**All research, experimentation, scripting, Java/APK development, on-device
testing, and documentation in this project was performed autonomously by a
coding agent (Claude Opus 4, running in [Pi][pi-agent]).** The human set up
Termux/SSH on the phone manually in early February (boot scripts, cron, initial
dark-photo scripts), then on March 21 handed the flash problem to the agent
with: *"Your goal is to have the phone take a photo with the flash. Don't stop
until you've succeeded."* From that point, the human's role was limited to
physically connecting/disconnecting the USB cable, starting SSH when asked,
and reviewing results. Every line of Java, every shell script revision, every
failed experiment, every workaround discovery, and this README were produced by
the agent across four sessions totalling 607 API turns and ~68M tokens ($51 at
API pricing). See [Agent session cost](#agent-session-cost) for the full
breakdown.

[claude-code]: https://claude.ai/code
[pi-agent]: https://github.com/mariozechner/pi

---

## The problem

An old Honor 5C phone (NEM-L21) is repurposed as a timelapse camera pointed at
the house mechanical room. The phone sits on mains power, connected to Wi-Fi,
running [Termux][termux] with SSH access. A cron job takes a photo every three
hours.

The immediate goal is to capture flash-illuminated images of the utility gauges
in this otherwise dark space – cold water meter, hot water meter, district
heating meter, and hydronic radiator heating pressure/temperature gauges. The
longer-term vision is to develop machine reading of these analog gauge faces to
automatically track water consumption, heating energy use, and system pressure
over time.

The catch: the room is dark. Without flash, photos are black (0.01% mean
brightness). Getting the flash to fire from a headless SSH session turned out to
be a six-week odyssey through Android's camera stack, Huawei's proprietary HAL,
and the limits of what an unprivileged app can do on a locked-down device.

[termux]: https://termux.dev/

---

## Full project timeline

### Week 1: Initial setup (February 5–6, 2026) — manual, no agent

The Honor NEM-L21 was set up manually by the human (direct Termux interaction,
no AI agents involved – confirmed by searching all Pi, Claude Code, and
pykoclaw session histories across both machines):

- Installed Termux, Termux:Boot, Termux:API, F-Droid
- Installed Dropbear SSH server, configured key-based auth
- Created `~/.termux/boot/start-services` to auto-start wake lock, dropbear,
  and crond on boot
- Wrote `timelapse.sh` – a loop-based timelapse script that ran continuously:
  ```bash
  while true; do
      termux-torch on &
      termux-camera-photo -c 0 "$DIR/$TIMESTAMP.jpg"
      termux-torch off
      sleep 10800  # 3 hours
  done
  ```
  Photos were black. The torch LED turned off the instant Camera2 opened a new
  session – they share the same hardware.

### Week 2: Cron-based automation (February 11, 2026) — manual, no agent

Replaced the loop with cronie for reliability (still manual human work):

- Installed `cronie`, `imagemagick` in Termux
- Wrote `flash-photo.sh` – brought Termux to foreground before torch+capture
  (EMUI required foreground for torch). Brightness: 0.4% – faint outlines.
- Wrote `photo_shoot.py` and `photo_shoot_v2.py` – Python wrappers with
  delays between torch and capture, wake lock management, cleanup. Same result:
  Camera2 kills the torch when opening a session.
- Wrote `service-watchdog.sh` – checks every 15 minutes that dropbear and crond
  are alive, restarts them if not
- Set up crontab: photo every 3 hours, watchdog every 15 minutes
- Wrote `timelapse-cron.sh` as the cron wrapper

The cron job ran faithfully for six weeks, producing consistently dark photos.
The phone ran on mains power, reporting via SSH. On March 8, internal storage
filled up – photos started failing with ENOSPC until old files were cleaned.

### Week 7: The flash breakthrough (March 21–22, 2026) — all agent work

Six weeks of dark photos later, the human handed the problem to a coding agent.
Three intensive Pi sessions in a single day cracked the problem, followed by a
fourth session the next morning for the APK, cron integration, and this repo.

#### Session 1: Exploration (Pi session `9c4eed4e`, 17:36–20:20)

The initial prompt: *"Your goal is to have the phone take a photo with the
flash. Brainstorm, experiment, test, iterate, make detailed notes. There are
some unsuccessful scripts in the Termux home directory. Don't stop until you've
succeeded."*

Six approaches were tried:

| # | Approach | Result |
|---|----------|--------|
| 1 | `termux-torch on` + `termux-camera-photo` | Torch killed by Camera2 session. 0.01% brightness. |
| 2 | Custom `FlashPhoto.java` via `app_process` | `systemMain()` → SIGKILL. `attach(false)` → SIGKILL. |
| 3 | `dalvikvm` with framework bootclasspath | `UnsatisfiedLinkError` – native libs unavailable. |
| 4 | Patch Termux:API smali (AE_MODE_ON → ALWAYS_FLASH) | Shared user ID requires matching APK signature. |
| 5 | Build standalone APK on device (ecj + dx + aapt) | aapt SDK 33 can't parse device framework-res SDK 24. |
| 6 | ADB `input swipe` to tap Huawei camera shutter | ✅ **Worked!** 30–33% brightness. But needs USB. |

**Key discovery:** The Huawei camera app's own flash works perfectly via UI
automation. The `input` command simulates a shutter tap, and the camera fires
the flash as configured (set to "Päälle" / On).

Mid-session, the user clarified: *"The phone will be mounted to a location where
it's running standalone connected to mains power but no other cables or devices
attached."* This ruled out ADB-dependent solutions.

The session ended with comprehensive documentation of all findings and a plan
for the self-loopback ADB approach.

#### Session 2: Self-loopback ADB (Pi session `12acdef4`, 20:20–21:29)

The user reconnected USB and enabled ADB TCP mode. The session implemented
self-loopback ADB:

1. Termux installs its own `adb` client (`pkg install android-tools`)
2. With USB connected, `adb tcpip 5555` enables TCP mode on adbd
3. Termux's adb connects to `127.0.0.1:5555` (device to itself)
4. `adb -s emulator-5554 shell input swipe ...` works from Termux scripts

Production script `flash-photo-adb-local.sh` was written and tested: wakes
screen, swipes to unlock, launches Huawei camera, taps shutter, copies photo.
Photos had 27–44% brightness.

**Critical discovery:** On this EMUI device, adbd unconditionally stops when USB
is physically disconnected, even with TCP mode active. Every persistence
workaround failed (setprop, settings, active TCP client). The USB cable had to
stay connected.

#### Session 3: The FlashPhoto APK (Pi session `d98b9bef`, 21:29–ongoing)

The goal: *"Find a work-around to enable autonomous periodic photo with flash
without a connected USB cable."*

**Insight:** Termux's `am` command (termux-am) works without ADB – it uses
`app_process` with its own JAR. If a standalone APK could take flash photos
when triggered by `am broadcast`, no ADB or USB would be needed.

The APK was built on the host computer (NixOS) since the phone's aapt was
incompatible:

- `aapt2` from nixpkgs for manifest compilation
- `javac` from JDK 17 for Java compilation
- `d8` from Android SDK build-tools 33 for dex conversion
- `android.jar` from Android SDK platform 33 for compilation classpath
- `jarsigner` from JDK 17 for APK signing

**Discovery: EMUI Camera2 HAL lies about flash.** The first APK used
`CONTROL_AE_MODE_ON_ALWAYS_FLASH` (mode 3). The capture result reported
`FLASH_STATE=FIRED` (3). Photo brightness: 0.08%. The HAL log told the truth:
`setFlashStatus() flash status:0` – the hardware flash never fired.

**Discovery: TORCH mode works.** Using `FLASH_MODE_TORCH` (mode 2) keeps the
LED on continuously via the flashlight code path – one that the Huawei HAL
*does* honor. With screen on: 28–29% brightness.

**Discovery: SurfaceTexture fails with screen off.** The standard Camera2
pattern uses a dummy `SurfaceTexture` for preview. This requires an OpenGL ES
context, which is unavailable when the screen is off. Error:
`createStream: Failed to query Surface consumer usage: No such device (-19)`.
Fix: use only `ImageReader` (no SurfaceTexture), discard preview frames.

Final result: **7–14% brightness with screen off, fully autonomous**, triggered
by `am broadcast` over SSH. The cron job was updated to use the new method.

---

## Device details

| Property | Value |
|----------|-------|
| Model | Honor NEM-L21 (Honor 5C / Honor 7 Lite) |
| Android | 7.0 (API 24), EMUI |
| Camera | Back camera ID 0, 4160×3120 (13MP) |
| AE modes | 0 (OFF), 1 (ON), 2 (AUTO_FLASH), 3 (ALWAYS_FLASH) |
| Flash | Available, but ALWAYS_FLASH mode broken in HAL |
| Storage | Internal 11GB + SD card 59GB |
| Termux user | u0_a133 (UID 10133) |
| FlashPhoto APK | com.flashphoto (UID 10100) |
| SSH | Dropbear on port 8022, key-based auth |
| Boot | Termux:Boot → wake lock + dropbear + crond |

---

## Brightness comparison

| Method | Brightness | Status |
|--------|-----------|--------|
| No flash (dark room) | 0.01% | Essentially black |
| Torch + termux-camera-photo | 0.01–0.4% | Torch killed by Camera2 session |
| Camera2 `ALWAYS_FLASH` | 0.08% | HAL ignores, flash doesn't fire |
| **FlashPhoto TORCH (screen off)** | **7–14%** | ✅ Production method |
| FlashPhoto TORCH (screen on) | 28–29% | Better but screen must be on |
| ADB + Huawei camera app | 27–44% | Requires USB cable |

---

## Building

### Prerequisites

- NixOS or `nix-shell` available
- Internet access (downloads Android SDK components on first build)

### Build

```bash
./build.sh
```

This produces `/tmp/flashphoto-build/flashphoto.apk`. A pre-built APK is
included in the repo as `flashphoto.apk`.

### Install (one-time, requires USB)

```bash
adb install flashphoto.apk
adb shell pm grant com.flashphoto android.permission.CAMERA
adb shell pm grant com.flashphoto android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant com.flashphoto android.permission.READ_EXTERNAL_STORAGE
```

After installation, USB can be disconnected permanently.

---

## Usage

### Take a single photo (from SSH)

```bash
ssh -p 8022 user@phone \
  'am broadcast -n com.flashphoto/.FlashReceiver \
   -a com.flashphoto.TAKE -e file /sdcard/DCIM/photo.jpg'
```

Wait ~5 seconds for the capture to complete.

### Using the wrapper script

```bash
ssh -p 8022 user@phone '~/bin/flash-photo-broadcast.sh'
ssh -p 8022 user@phone '~/bin/flash-photo-broadcast.sh ~/photos/custom.jpg'
```

The wrapper handles directory creation, copies from shared storage to Termux
private storage, measures brightness via ImageMagick, and cleans up.

---

## Termux setup

See the `termux/` directory for all scripts deployed on the phone.

### Boot script (`termux/boot/start-services`)

Runs automatically on device boot via Termux:Boot:

```bash
termux-wake-lock   # prevent CPU sleep
dropbear -s        # SSH server (key-only auth)
crond              # cron daemon for timelapse
```

### Crontab (`termux/crontab`)

```cron
# Service watchdog – restart dropbear/crond if dead
*/15 * * * * ~/bin/service-watchdog.sh

# Timelapse photo every 3 hours
0 */3 * * * ~/bin/timelapse-cron.sh >> ~/cron.log 2>&1
```

### Installed Termux packages

```
android-tools  apksigner  cronie  dropbear  ecj  imagemagick
```

### Scripts on the phone (`termux/bin/`)

| Script | Purpose | Date |
|--------|---------|------|
| `flash-photo-broadcast.sh` | **Production** – FlashPhoto APK via broadcast | Mar 22 |
| `timelapse-cron.sh` | Cron wrapper calling flash-photo-broadcast.sh | Mar 22 |
| `service-watchdog.sh` | Restarts dropbear/crond if dead | Feb 11 |
| `flash-photo-adb-local.sh` | Legacy – self-loopback ADB (needs USB) | Mar 21 |
| `flash-photo-v2.sh` | Early ADB input method | Mar 21 |
| `set-flash-on.sh` | One-time: set Huawei camera flash to "On" | Mar 21 |
| `flash-photo.sh` | Original torch+camera attempt | Feb 11 |
| `photo_shoot.py` | Python torch+camera wrapper | Feb 11 |
| `photo_shoot_v2.py` | Improved Python with wake lock | Feb 11 |
| `timelapse.sh` | Original loop-based timelapse (replaced by cron) | Feb 6 |

---

## How it works (technical)

The APK contains a single exported `BroadcastReceiver` (`FlashReceiver`) that:

1. Receives `com.flashphoto.TAKE` broadcast with `file` extra (output path)
2. Calls `goAsync()` to extend receiver lifetime beyond 10 seconds
3. Opens Camera2 on a background `HandlerThread`
4. Creates a capture session with **only an `ImageReader`** surface (no
   `SurfaceTexture` – avoids OpenGL dependency that fails with screen off)
5. Runs repeating preview captures with `FLASH_MODE_TORCH` for 2 seconds
   (LED on, AE converges; preview frames discarded via `AtomicBoolean` flag)
6. Fires a single still capture with `FLASH_MODE_TORCH`
7. `ImageReader.OnImageAvailableListener` saves the JPEG and cleans up

### Key design decisions

- **TORCH instead of ALWAYS_FLASH** – the EMUI HAL ignores standard flash
  modes but honors torch mode (shared code path with the flashlight feature)
- **No SurfaceTexture** – fails when screen is off due to missing OpenGL
  context; `ImageReader`-only sessions work headlessly
- **`goAsync()`** – BroadcastReceivers normally have a 10-second limit; this
  extends it for the full capture sequence (~5 seconds)

### What failed and why

| Approach | Failure mode |
|----------|-------------|
| `termux-torch` + `termux-camera-photo` | Camera2 session takes exclusive hardware control, kills torch |
| Custom Java via `app_process` | Android kills unregistered processes (SIGKILL) |
| `dalvikvm` with framework classpath | Native libs unavailable outside app_process |
| Patching Termux:API smali | Shared user ID requires matching APK signature |
| On-device APK build (ecj/dx/aapt) | aapt SDK version mismatch with device framework |
| Camera2 `CONTROL_AE_MODE_ON_ALWAYS_FLASH` | EMUI HAL ignores; reports FIRED but doesn't fire |
| Camera2 `FLASH_MODE_SINGLE` | Same – HAL ignores |
| `/system/bin/input` from Termux | Needs shell UID; SIGKILL under app UID |
| Write to `/dev/input/eventN` | Permission denied – not in input group |
| SurfaceTexture for preview | Needs OpenGL context; fails with screen off |

---

## Future work

### Immediate

- [ ] Test full reboot cycle without USB (verify boot script + broadcast method)
- [ ] Optimize torch warm-up time (currently 2s – could be shorter)
- [ ] Add retry logic for transient camera errors
- [ ] Explore manual exposure/ISO control for more consistent brightness
- [ ] Set up photo retrieval pipeline (SCP/rsync from phone to server)

### Gauge reading (next phase)

The phone will be pointed at the house mechanical room gauges:

- **Cold water meter** – cumulative m³ consumption
- **Hot water meter** – cumulative m³ consumption
- **District heating energy meter** – cumulative MWh from the district heating
  network (*kaukolämpö*)
- **Hydronic heating gauges** – pressure (bar) and temperature (°C) of the
  radiator circuit (*vesikiertoinen patterilämmitys*)

Planned work:

- [ ] Mount phone in a fixed position with gauges in frame
- [ ] Develop image segmentation to locate each gauge dial in the photo
- [ ] Train or use OCR/CV model to read analog gauge needle positions
- [ ] Log gauge readings over time (database or CSV)
- [ ] Alert on anomalies (sudden consumption, pressure drop, temperature spike)
- [ ] Dashboard for historical trends

---

## Agent session cost

All work was performed by Claude Opus 4 across four Pi sessions on March 21–22,
2026. Token counts and costs are from the Anthropic API usage logs embedded in
each session file.

| Session | Focus | Turns | Output tokens | Cache (read+write) | Cost |
|---------|-------|------:|-------------:|-----------:|-----:|
| `9c4eed4e` | Exploration, 6 failed approaches, first ADB flash | 213 | 79,753 | 25.1M + 799K | $19.54 |
| `7138154e` | Brief intermediate | 6 | 1,087 | 120K + 39K | $0.33 |
| `12acdef4` | Self-loopback ADB, production script | 210 | 60,025 | 20.0M + 254K | $13.11 |
| `d98b9bef` | FlashPhoto APK, TORCH discovery, cron setup | 178 | 95,561 | 20.6M + 812K | $17.74 |
| **Total** | | **607** | **236,426** | **65.8M + 1.9M** | **$50.73** |

The high cache-read volume reflects the iterative nature of the work: each API
turn re-sends the full conversation context (including tool results from SSH
commands, logcat output, Java source, and build logs) which is served from
Anthropic's prompt cache.

---

## License

MIT
