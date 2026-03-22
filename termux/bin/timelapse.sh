#!/data/data/com.termux/files/usr/bin/bash
# Timelapse: take flash photo every 3 hours, save to monthly folders
INTERVAL=10800  # 3 hours in seconds

take_photo() {
    MONTH=$(date +%Y-%m)
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    DIR="$HOME/photos/$MONTH"
    mkdir -p "$DIR"
    
    # Wake screen via termux-api (needed for camera)
    termux-wake-lock
    sleep 2
    
    # Flash photo
    termux-torch on &
    TORCH_PID=$!
    sleep 1
    termux-camera-photo -c 0 "$DIR/$TIMESTAMP.jpg"
    sleep 0.5
    termux-torch off
    kill $TORCH_PID 2>/dev/null
    
    SIZE=$(du -h "$DIR/$TIMESTAMP.jpg" 2>/dev/null | cut -f1)
    echo "$(date): Captured $DIR/$TIMESTAMP.jpg ($SIZE)"
}

while true; do
    take_photo
    sleep $INTERVAL
done
