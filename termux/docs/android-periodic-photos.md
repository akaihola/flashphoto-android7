# Automatic Periodic Photos on Android

Taking photos at set intervals with flash, and making them accessible to other systems.

---

## Option 1: Termux CLI (No App Needed)

The simplest approach if Termux is installed. Works over SSH.

### Requirements
- Termux + Termux:API (from F-Droid)
- `termux-api` package: `pkg install termux-api`

### Commands
```bash
# Camera info (list cameras, resolutions, capabilities)
termux-camera-info

# Take a photo (back camera = 0, front = 1)
termux-camera-photo -c 0 /path/to/photo.jpg

# Flash/torch control
termux-torch on
termux-torch off
```

### Take a Photo with Flash
`termux-camera-photo` doesn't have a flash flag, but the torch works as a substitute:
```bash
termux-torch on
sleep 1
termux-camera-photo -c 0 ~/photos/$(date +%Y%m%d_%H%M%S).jpg
termux-torch off
```

### Periodic Capture via Cron
```bash
# Create photos directory
mkdir -p ~/photos

# Edit crontab
crontab -e
```

Example crontab entry (every 15 minutes):
```cron
*/15 * * * * termux-torch on && sleep 1 && termux-camera-photo -c 0 ~/photos/$(date +\%Y\%m\%d_\%H\%M\%S).jpg && termux-torch off
```

### Accessing the Photos

**SSH/SCP** (pull from another machine):
```bash
scp -P 8022 user@phone-ip:~/photos/*.jpg ./local-folder/
```

**HTTP server** (browse from any device on LAN):
```bash
python -m http.server 8080 -d ~/photos
# Photos available at http://phone-ip:8080/
```

**Syncthing** (auto-sync to another device):
- Install Syncthing from F-Droid on both devices
- Share the `~/photos` folder

### Notes
- The back camera on Nokia 6.2 (kuuskaks) supports up to 4608×3456
- Back camera supports `CONTROL_AE_MODE_ON_ALWAYS_FLASH` and `CONTROL_AE_MODE_ON_AUTO_FLASH`
- Front camera does not support flash modes
- Termux:API app must be installed from F-Droid (not just the `termux-api` package)
- Phone screen can be off — the command works in background

---

## Option 2: Open Camera (F-Droid)

**Package:** `net.sourceforge.opencamera`
**Source:** https://f-droid.org/packages/net.sourceforge.opencamera/

### Features
- **Repeat mode** with intervals from 1 second to 2 hours
- Unlimited repeat count
- Flash on/off/auto/torch
- Timer + auto-repeat for scheduled captures
- Full manual controls (ISO, exposure, white balance, focus)
- Geotagging, timestamps

### Setup for Interval Capture
1. Install from F-Droid
2. Settings → Camera controls → **Repeat mode**: Unlimited
3. Settings → Camera controls → **Repeat mode interval**: e.g. 10 minutes
4. Set flash mode to On or Torch
5. Press shutter — it keeps capturing at the set interval

### Limitations
- No built-in upload or HTTP server
- App must stay in foreground (screen can dim but app must be active)
- Combine with Syncthing or FolderSync for automatic photo transfer

---

## Option 3: Timer Camera (Google Play)

**Package:** `com.cae.timercamera`
**Source:** https://play.google.com/store/apps/details?id=com.cae.timercamera

### Features
- **Clock-Timer**: schedule daily captures (e.g. every 10 min between 8:30–20:30)
- Burst mode with configurable intervals (seconds to hours)
- Flash on/off/auto/torch
- Video recording with max duration setting
- Multiple resolutions including 4K

### Setup for Daily Scheduled Capture
1. Enable **Clock-Timer** trigger
2. Set start time and daily schedule
3. Set **Custom Burst** count (e.g. 72 for 12 hours at 10-min intervals)
4. Set **Burst mode interval** (e.g. 10 minutes)
5. Configure flash as needed

### Limitations
- Not on F-Droid (Google Play only)
- No built-in upload/serve — pair with a sync app
- Ad-supported (free version)

---

## Option 4: IP Webcam (Google Play)

**Package:** `com.pas.webcam` (free) / `com.pas.webcam.pro` (€4)
**Source:** https://play.google.com/store/apps/details?id=com.pas.webcam

### Features
- **Built-in HTTP server** — other systems pull photos on demand
- Flash/torch control via HTTP API
- Upload to FTP/SFTP/Dropbox/Email (via Filoader plugin)
- Motion detection with sound trigger
- Tasker integration
- Home Assistant integration
- Video streaming (MJPEG, RTSP)

### Key URLs (once running)
```
http://phone-ip:8080/shot.jpg          # Grab current snapshot
http://phone-ip:8080/enabletorch       # Flash on
http://phone-ip:8080/disabletorch      # Flash off
http://phone-ip:8080/photo.jpg         # Take photo and return it
http://phone-ip:8080/photoaf.jpg       # Take photo with autofocus
```

### Periodic Capture from Another System
No app-side scheduling needed — just poll from your server:
```bash
# Cron job on the receiving machine (every 5 minutes)
*/5 * * * * curl -s http://phone-ip:8080/photoaf.jpg -o ~/photos/$(date +\%Y\%m\%d_\%H\%M\%S).jpg
```

### Limitations
- Not on F-Droid (Google Play only)
- App must stay in foreground
- Uses significant battery (HTTP server always running)

---

## Option 5: TimeLapseCam (F-Droid)

**Package:** `org.woheller69.TimeLapseCam`
**Source:** https://f-droid.org/en/packages/org.woheller69.TimeLapseCam/

### Features
- Schedule recording on specific dates
- Save as MP4 video or JPEG sequence
- Multiple resolution options
- Auto-stops on low battery or disk space

### Limitations
- More video-oriented than photo-oriented
- No upload/serve built in
- Simpler feature set than Open Camera

---

## Comparison

| Feature | Termux CLI | Open Camera | Timer Camera | IP Webcam | TimeLapseCam |
|---------|-----------|-------------|--------------|-----------|-------------|
| **F-Droid** | ✅ | ✅ | ❌ | ❌ | ✅ |
| **FOSS** | ✅ | ✅ | ❌ | ❌ | ✅ |
| **Flash control** | ✅ (torch) | ✅ | ✅ | ✅ (HTTP) | ❌ |
| **Interval capture** | ✅ (cron) | ✅ (repeat) | ✅ (burst) | ✅ (polling) | ✅ |
| **Scheduled start** | ✅ (cron) | ❌ | ✅ (clock) | ❌ | ✅ |
| **Upload/serve** | ✅ (SSH/HTTP) | ❌ | ❌ | ✅ (HTTP/FTP) | ❌ |
| **Works with screen off** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **No GUI needed** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Remote trigger** | ✅ (SSH) | ❌ | ❌ | ✅ (HTTP) | ❌ |
| **Battery usage** | Low | Medium | Medium | High | Medium |

---

## Recommendation

**For headless/automated use:** Termux CLI — works over SSH, no GUI, lowest battery usage, fully scriptable, works with screen off. Combine with cron for scheduling and `python -m http.server` or SSH for retrieval.

**For GUI app on F-Droid:** Open Camera — most mature, full manual controls, repeat mode up to 2 hours. Pair with Syncthing for auto-sync.

**For pull-based access (another system grabs photos):** IP Webcam — built-in HTTP server, other systems request snapshots on demand. Google Play only.
