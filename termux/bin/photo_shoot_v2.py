#!/usr/bin/env python3
"""Photo shoot with improved flash support for Termux/Android.

SUPERSEDED by flash-photo-broadcast.sh

FAILURE MODE: Same fundamental issue as photo_shoot.py and flash-photo.sh.
Adding a wake lock and a longer 2s flash delay doesn't help because Camera2
kills the torch synchronously at session open, regardless of timing. The wake
lock keeps the CPU alive but doesn't affect the camera hardware exclusion.
Photos are black (0.01% brightness).
"""

import subprocess
import sys
from datetime import datetime
from pathlib import Path
import time


def take_photo(output_path: str | None = None, camera_id: int = 0, flash_delay: float = 2.0) -> tuple[bool, str]:
    """Take a photo with flash on Android via Termux.
    
    Args:
        output_path: Where to save the photo (default: ~/photos/YYYY-MM/YYYYMMDD_HHMMSS.jpg)
        camera_id: 0 for back camera, 1 for front camera
        flash_delay: Seconds to wait for torch to warm up (default: 2.0)
        
    Returns:
        (success: bool, message: str)
    """
    # Default path
    if output_path is None:
        photo_dir = Path.home() / "photos" / datetime.now().strftime("%Y-%m")
        photo_dir.mkdir(parents=True, exist_ok=True)
        output_path = photo_dir / f"{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg"
    else:
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
    
    output_path = Path(output_path)
    
    torch_on = False
    wake_locked = False
    
    try:
        # Step 0: Keep screen awake (required for flash to work)
        print("Taking wake lock...", file=sys.stderr)
        subprocess.run(["termux-wake-lock"], capture_output=True, timeout=5)
        wake_locked = True
        
        # Step 1: Turn on torch (flash)
        print(f"Turning on torch (waiting {flash_delay}s for warm-up)...", file=sys.stderr)
        result = subprocess.run(["termux-torch", "on"], capture_output=True, text=True, timeout=5)
        if result.returncode != 0:
            return False, f"Failed to turn on torch: {result.stderr}"
        torch_on = True
        
        # Step 2: Wait for LED to stabilize and camera to adjust
        time.sleep(flash_delay)
        
        # Step 3: Take photo
        print("Taking photo...", file=sys.stderr)
        result = subprocess.run(
            ["termux-camera-photo", "-c", str(camera_id), str(output_path)],
            capture_output=True,
            text=True,
            timeout=30
        )
        
        if result.returncode != 0:
            return False, f"Camera error: {result.stderr}"
        
        if not output_path.exists():
            return False, "Photo file not created"
        
        file_size = output_path.stat().st_size
        if file_size == 0:
            output_path.unlink()
            return False, "Photo is 0 bytes"
        
        return True, f"Photo saved: {output_path} ({file_size} bytes)"
        
    except subprocess.TimeoutExpired as e:
        return False, f"Timeout: {e}"
    except Exception as e:
        return False, f"Error: {e}"
    finally:
        # Cleanup
        if torch_on:
            print("Turning off torch...", file=sys.stderr)
            subprocess.run(["termux-torch", "off"], capture_output=True, timeout=5)
        if wake_locked:
            print("Releasing wake lock...", file=sys.stderr)
            subprocess.run(["termux-wake-unlock"], capture_output=True, timeout=5)


if __name__ == "__main__":
    output = sys.argv[1] if len(sys.argv) > 1 else None
    # Use longer flash delay
    success, message = take_photo(output, flash_delay=2.0)
    print(message)
    sys.exit(0 if success else 1)
