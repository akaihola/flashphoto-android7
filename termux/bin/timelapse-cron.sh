#!/data/data/com.termux/files/usr/bin/bash
# Timelapse cron wrapper - takes flash photo via FlashPhoto APK broadcast
MONTH=$(date +%Y-%m)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p ~/photos/$MONTH
~/bin/flash-photo-broadcast.sh ~/photos/$MONTH/$TIMESTAMP.jpg >> ~/timelapse.log 2>&1
