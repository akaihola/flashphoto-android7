# FlashPhoto for Android 7 (EMUI)

[![Built with Claude Code](https://img.shields.io/badge/Built_with-Claude_Code-6f42c1?logo=anthropic&logoColor=white)](https://claude.ai/code)

> This project is developed by an AI coding agent ([Claude][claude-code], via
> [Pi][pi-agent]), with human oversight and direction.

A minimal Android APK that takes flash-illuminated photos via Camera2 API,
triggered by broadcast intent from Termux. Built to work autonomously on an
Honor NEM-L21 (Android 7.0, EMUI) deployed for periodic timelapse photography –
**no USB cable, no screen wake, no human interaction required**.

**All research, experimentation, scripting, Java/APK development, on-device
testing, and documentation in this project was performed autonomously by coding
agents.** In early February, [openclaw-termux][openclaw-termux] and its agent
persona Tyko, running on gogo and backed by Claude Opus 4.5, researched
Android camera options, wrote the initial timelapse scripts, and documented
ADB/Termux tricks. Then on March 21, a Pi agent on atom, using mostly Claude
Opus 4.6 and partly Claude Sonnet 4.6, tackled the flash problem: *"Your goal
is to have the phone take a photo with the flash. Don't stop until you've
succeeded."* The human's role throughout was providing the goal, answering
clarifying questions, physically connecting/disconnecting the USB cable, and
starting SSH when asked. The failed attempts described here were not
hand-written inputs from the human – they came from the agents' own
experiments, logs, errors, and image measurements. Every line of code, every
failed experiment, every workaround discovery, and this README were produced by
agents across **16+ sessions totalling 1,822+ API turns and ~174M+ tokens
($165+ at API pricing)**. See [Agent session cost](#agent-session-cost) for the
full breakdown.

[claude-code]: https://claude.ai/code
[pi-agent]: https://github.com/mariozechner/pi
[openclaw-termux]: https://github.com/explysm/openclaw-termux

---

## The problem

An old Honor 5C phone (NEM-L21) is repurposed as a timelapse camera pointed at
the house mechanical room. The phone sits on mains power, connected to Wi-Fi,
running [Termux][termux] with SSH access. A cron job takes a photo every three
hours.

The immediate goal is to capture flash-illuminated images of the utility gauges
in this otherwise dark space – cold water meter, hot water meter, and hydronic
radiator heating pressure/temperature gauges. The longer-term vision is to
develop machine reading of these gauge faces to automatically track water
consumption and heating system status over time.

The catch: the room is dark. Without flash, photos are black (0.01% mean
brightness). Getting the flash to fire from a headless SSH session turned out to
be a six-week odyssey through Android's camera stack, Huawei's proprietary HAL,
and the limits of what an unprivileged app can do on a locked-down device.

[termux]: https://termux.dev/

---

## Full project timeline

### Week 1: Initial setup (February 5–6, 2026) — openclaw-termux / Tyko on gogo

The Honor NEM-L21 was set up by [openclaw-termux][openclaw-termux] and its
agent persona Tyko, running in Termux on gogo and backed by Claude Opus 4.5.
The agent researched 5 approaches for periodic Android photography (see
`termux/docs/android-periodic-photos.md`), wrote the ADB/Termux knowledge base
(`android-tricks.md`), created the initial `honor-timelapse.sh` trigger
script, and documented everything. Archived session logs are under
`/home/akaihola/my-knowledge/archives/openclaw-sessions/` (9 sessions, 788 API
turns, $59.14):

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

### Week 2: Cron-based automation (February 11, 2026) — manual by human

Replaced the loop with cronie for reliability (manual human work on the phone,
no agent sessions found for this period):

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

Six weeks of dark photos later, the human handed the problem to a Pi coding
agent on atom. Three intensive Pi sessions in a single day, using Claude Opus
4.6, cracked the problem, followed by a fourth session the next morning for the
APK, cron integration, and this repo.

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

Initial result: 7–14% brightness with screen off, but intermittently dark
(0.3%) due to timing race between torch and `TEMPLATE_STILL_CAPTURE`.

#### Session 4: Preview-frame strategy (March 22, 2026)

A follow-up session diagnosed the intermittent failures: on Huawei EMUI, the
Camera2 HAL kills the torch LED when `TEMPLATE_STILL_CAPTURE` fires. The fix:
**save a TORCH-lit preview frame directly** from the JPEG ImageReader, never
issuing a still capture at all. This yielded **consistent 12–16% brightness**
on both battery and AC power. See [EXPERIMENTS.md] for detailed timing results.

Final result: **12–16% brightness with screen off, fully autonomous**, triggered
by `am broadcast` from a Termux cron job.

#### Session 5: Production tuning and documentation (March 22–23, 2026)

After the preview-frame breakthrough, several shorter Pi sessions refined the
system for real use in the mechanical room:

- Replaced the painfully slow ImageMagick brightness measurement with Pillow on
  the phone
- Tested the phone on AC power in the actual installation room
- Added manual exposure controls (`iso`, `exposure_ms`) to compensate for the
  auto-exposure algorithm underexposing a mostly dark scene
- Settled on **ISO 800 + 100 ms** as the production default, raising measured
  brightness to **24%** and making the gauges clearly readable
- Updated the wrapper script, experiments log, README, and the Finnish
  narrative document to reflect the production setup
- Disabled Huawei's background killers (`com.huawei.powergenie` and
  `com.huawei.android.hwaps`) over ADB so they would stop killing cron and
  Dropbear

At this point the project had crossed the line from "flash works" to "usable
production photos arrive automatically".

#### Session 6: Focus fix and deployment (March 25–26, 2026)

Once the camera was producing bright images reliably, a new problem showed up:
many production photos were soft and slightly out of focus. A Pi session on
March 25 analyzed the Camera2 flow and identified the root cause: the APK was
saving whatever TORCH-lit preview frame happened to arrive after warmup, with
no explicit autofocus confirmation.

The fix added three pieces:

- **AF-lock-before-save** – trigger `AF_TRIGGER_START` after warmup and wait
  for `FOCUSED_LOCKED` / `NOT_FOCUSED_LOCKED`
- **Manual focus mode** – allow a calibrated `focus_diopters` override for a
  fixed-mount installation
- **Diagnostic focus logging** – record AF state, lens movement, and focus
  distance for each frame

On March 26 the new APK was deployed and tested on the phone. ADB logcat showed
AF locking around **1.13 diopters** (~88 cm), while a manually tuned focus
setting around **1.3 diopters** produced the sharpest result in the actual
mechanical room. The production Termux wrapper was updated to use the calibrated
manual focus setting, and an end-to-end test produced a **24.3% brightness**
image with visibly sharper gauge faces and labels. The comparison shots were
also copied into the follow-on `gauge-reader` project for machine-vision
experiments.

[EXPERIMENTS.md]: EXPERIMENTS.md

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
| **FlashPhoto TORCH + manual exposure (production)** | **24%** | ✅ Production method (ISO 800, 100ms) |
| FlashPhoto TORCH + auto-exposure (small room) | 12–16% | Works in small reflective spaces |
| FlashPhoto TORCH + auto-exposure (large dark room) | 0.8–1.6% | AE underexposes |
| FlashPhoto TORCH STILL_CAPTURE (screen off) | 0.3–14% | Unreliable – HAL kills torch |
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
private storage, measures brightness via Pillow, and cleans up.

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
android-tools  apksigner  cronie  dropbear  ecj  python-pillow
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
5. Runs repeating preview requests with `FLASH_MODE_TORCH` on the JPEG
   ImageReader for 2 seconds (LED on, AE converges; preview frames discarded)
6. **Waits for autofocus lock** before saving (or uses manual focus distance) –
   sends `AF_TRIGGER_START`, monitors `CONTROL_AF_STATE` for `FOCUSED_LOCKED`,
   falls back to saving anyway after a configurable timeout
7. Saves a TORCH-lit preview frame directly – **no `STILL_CAPTURE`**
   (the Huawei HAL kills the torch when a still capture request fires)
8. `ImageReader.OnImageAvailableListener` saves the JPEG and cleans up

Optional broadcast extras for tuning:
- `--ei warmup <ms>` – torch warmup before AF lock (default: 2000)
- `--ei skip <N>` – preview frames to skip after warmup (manual focus only, default: 5)
- `--ei iso <value>` – manual ISO sensitivity (0 = auto, try 400–1600)
- `--ei exposure_ms <value>` – manual exposure time in ms (0 = auto, try 33–100)
- `--ef focus_diopters <float>` – manual focus distance in diopters (default: -1 = autofocus).
  Examples: `2.0` for ~50 cm, `3.3` for ~30 cm, `1.0` for ~1 m, `0.0` for infinity.
  Best for fixed-mount setups where the distance to the subject doesn't change.
- `--ei af_timeout <ms>` – AF lock timeout before saving anyway (default: 3000).
  If autofocus can't converge within this time, the best available frame is saved
  rather than losing the shot.

**Production defaults:** ISO 800 + 100 ms exposure. Auto-exposure fails in
large dark rooms because the AE algorithm sees mostly darkness and
underexposes despite the torch being on. See [EXPERIMENTS.md] for the
full tuning data.

#### Focus modes

**Autofocus (default):** After the torch warmup period, the receiver triggers
an explicit AF lock (`AF_TRIGGER_START`) and waits for `FOCUSED_LOCKED` or
`NOT_FOCUSED_LOCKED` before saving. Every frame logs AF state, focus distance,
and lens state via `logcat -s FlashPhoto` for diagnostics. If the AF lock
doesn't converge within `af_timeout` ms, the frame is saved anyway.

**Manual focus (`--ef focus_diopters <value>`):** Sets `CONTROL_AF_MODE_OFF`
and a fixed `LENS_FOCUS_DISTANCE`. Ideal for a phone mounted at a known
distance from gauges – eliminates AF hunting entirely. After warmup, skips
`skip` frames to let the lens settle, then saves. To calibrate: take a test
shot with autofocus enabled, check the logged focus distance, then use that
value for subsequent shots.

### Key design decisions

- **TORCH instead of ALWAYS_FLASH** – the EMUI HAL ignores standard flash
  modes but honors torch mode (shared code path with the flashlight feature)
- **Preview-frame save instead of STILL_CAPTURE** – the HAL kills the torch
  when `TEMPLATE_STILL_CAPTURE` fires; saving a TORCH-lit preview frame from
  the JPEG ImageReader avoids this entirely (see [EXPERIMENTS.md])
- **AF lock before save** – `CONTINUOUS_PICTURE` AF can be mid-hunt when a
  frame is captured; triggering `AF_TRIGGER_START` and waiting for
  `FOCUSED_LOCKED` ensures the lens has converged before saving
- **Manual focus option** – for fixed-mount setups, `CONTROL_AF_MODE_OFF` with
  a calibrated `LENS_FOCUS_DISTANCE` eliminates AF hunting entirely
- **Save on AF timeout** – if the HAL doesn't report AF convergence (broken
  state reporting or genuinely unfocusable scene), the frame is saved anyway
  after a timeout rather than losing the shot
- **No SurfaceTexture** – fails when screen is off due to missing OpenGL
  context; `ImageReader`-only sessions work headlessly
- **`goAsync()`** – BroadcastReceivers normally have a 10-second limit; this
  extends it for the full capture sequence (~5–8 seconds with AF lock)

[EXPERIMENTS.md]: EXPERIMENTS.md

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

- [ ] Set up photo retrieval pipeline (SCP/rsync from phone to server)
- [ ] Address PowerGenie killing Termux services (uninstall via ADB or
  mark Termux as protected in EMUI battery settings)

### Gauge reading (next phase)

The phone will be pointed at the house mechanical room gauges:

- **Cold water meter** – cumulative m³ consumption
- **Hot water meter** – cumulative m³ consumption
- **Hydronic heating pressure gauge** – bar, analog dial
- **Hydronic heating temperature gauge** – °C, analog dial

Planned work:

- [ ] Mount phone in a fixed position with gauges in frame
- [ ] Develop image segmentation to locate each gauge dial in the photo
- [ ] Train or use OCR/CV model to read analog gauge needle positions
- [ ] Log gauge readings over time (database or CSV)
- [ ] Alert on anomalies (sudden consumption, pressure drop, temperature spike)
- [ ] Dashboard for historical trends

---

## Agent session cost

Token counts and costs are from the Anthropic API usage logs embedded in each
session file. Three agent platforms were used across the project's lifetime.

### Phase 1: OpenClaw agent on gogo (February 5–7, 2026)

The OpenClaw agent ("Clawd", later Tyko) ran on gogo, accessed via Telegram.
Sessions stored in `/home/agent/.openclaw/agents/clawd/sessions/`.

| Session | Focus | Turns | Output tokens | Cache (read+write) | Cost |
|---------|-------|------:|-------------:|-----------:|-----:|
| `d16c0a1c` | Research: 5 Android camera approaches | 161 | 33,777 | 9.9M + 1.1M | $10.09 |
| `46214d5a` | Phone setup, Termux, SSH, ADB | 38 | 13,035 | 2.8M + 1.5M | $5.71 |
| `58f429ac` | android-periodic-photos.md docs | 26 | 7,000 | 674K + 186K | $1.38 |
| `07ccd2b0` | Timelapse scripts, boot config | 474 | 98,037 | 41.5M + 3.2M | $39.05 |
| 5 smaller | ADB tricks, cleanup, migration | 89 | 17,986 | 1.6M + 676K | $2.90 |
| **Subtotal** | | **788** | **169,835** | **56.5M + 6.7M** | **$59.14** |

### Phase 2: Pi agent on atom (March 21–26, 2026)

Pi sessions on atom. Early sessions used mostly Claude Opus 4.6 and partly
Claude Sonnet 4.6; the later tuning/focus sessions are recorded as Claude Opus
4.6 in the Pi logs. Session logs in `~/.pi/agent/sessions/`.

| Session | Focus | Turns | Output tokens | Cache (read+write) | Cost |
|---------|-------|------:|-------------:|-----------:|-----:|
| `9c4eed4e` | Exploration, 6 failed approaches, first ADB flash | 213 | 79,753 | 25.1M + 799K | $19.54 |
| `7138154e` | Brief intermediate | 6 | 1,087 | 120K + 39K | $0.33 |
| `12acdef4` | Self-loopback ADB, production script | 210 | 60,025 | 20.0M + 254K | $13.11 |
| `d98b9bef` | FlashPhoto APK, TORCH discovery, cron setup | 178 | 95,561 | 20.6M + 812K | $17.74 |
| `1df67ec4` | Production tuning, manual exposure docs, PowerGenie removal, gauge-reader seed repo | 290 | 76,678 | 33.7M + 1.6M | $30.37 |
| `37bfb929` | Clarify and reword Finnish/English project narrative | 40 | 9,030 | ~0 + ~0* | $19.97 |
| `e132d2bf` | Focus diagnosis, AF lock/manual focus implementation, build, deploy, test | 97 | 32,033 | 6.8M + 169K | $5.27 |
| **Subtotal** | | **1,034** | **354,167** | **106.2M + 3.7M** | **$106.33** |

\* Pi logged zero cache tokens for `37bfb929`, but the billed total is still
present in the session usage metadata.

### Grand total

| | Turns | Output tokens | Cache (read+write) | Cost |
|---|------:|-------------:|-----------:|-----:|
| **All 16 sessions** | **1,822** | **524,002** | **162.7M + 10.4M** | **$165.47** |

The high cache-read volume reflects the iterative nature of the work: each API
turn re-sends the full conversation context (including tool results from SSH
commands, logcat output, Java source, and build logs) which is served from
Anthropic's prompt cache.

---

## License

MIT
