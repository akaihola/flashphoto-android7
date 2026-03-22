#!/usr/bin/env python3
"""Photo shoot with flash support for Termux/Android.

SUPERSEDED by flash-photo-broadcast.sh

FAILURE MODE: Same as flash-photo.sh – Camera2 API (via termux-camera-photo)
kills the torch LED when opening a camera session. The 0.5s delay between
torch-on and capture is irrelevant because the kill happens at session open,
not at a timing boundary. Photos are black (0.01% brightness).
"""

import subprocess
import sys
from datetime import datetime
from pathlib import Path


def take_photo(output_path: str | None = None, camera_id: int = 0) -> tuple[bool, str]:
    """Take a photo with flash on Android via Termux.
    
    Args:
        output_path: Where to save the photo (default: ~/photos/YYYY-MM/YYYYMMDD_HHMMSS.jpg)
        camera_id: 0 for back camera, 1 for front camera
        
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
    
    try:
        # Step 1: Turn on torch (flash) in background
        torch_proc = subprocess.Popen(
            ["termux-torch", "on"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        
        # Step 2: Wait for LED to stabilize
        import time
        time.sleep(0.5)
        
        # Step 3: Take photo
        result = subprocess.run(
            ["termux-camera-photo", "-c", str(camera_id), str(output_path)],
            capture_output=True,
            text=True,
            timeout=30
        )
        
        # Step 4: Turn off torch
        subprocess.run(
            ["termux-torch", "off"],
            capture_output=True,
            timeout=5
        )
        
        # Check result
        if result.returncode != 0:
            return False, f"Camera error: {result.stderr}"
        
        if not output_path.exists():
            return False, "Photo file not created"
        
        file_size = output_path.stat().st_size
        if file_size == 0:
            output_path.unlink()
            return False, "Photo is 0 bytes (screen may be off)"
        
        # Get image dimensions if possible
        try:
            from PIL import Image
            with Image.open(output_path) as img:
                width, height = img.size
                return True, f"Photo saved: {output_path} ({file_size} bytes, {width}x{height})"
        except ImportError:
            return True, f"Photo saved: {output_path} ({file_size} bytes)"
            
    except subprocess.TimeoutExpired:
        return False, "Camera timeout"
    except Exception as e:
        return False, f"Error: {e}"


if __name__ == "__main__":
    output = sys.argv[1] if len(sys.argv) > 1 else None
    success, message = take_photo(output)
    print(message)
    sys.exit(0 if success else 1)
