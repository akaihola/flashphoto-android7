#!/data/data/com.termux/files/usr/bin/bash
# Service watchdog - restart dropbear and crond if dead

# Check dropbear
if ! pgrep -x dropbear > /dev/null; then
    echo "$(date): dropbear not running, starting..." >> ~/watchdog.log
    dropbear -p 8022 -r ~/.ssh/id_dropbear
fi

# Check crond
if ! pgrep -x crond > /dev/null; then
    echo "$(date): crond not running, starting..." >> ~/watchdog.log
    crond
fi
