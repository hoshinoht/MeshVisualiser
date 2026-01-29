#!/bin/zsh
# List connected Android devices with model info
set -euo pipefail

output=$(adb devices | awk 'NR>1 && /device$/ {print $1}')
if [[ -z "$output" ]]; then
    echo "No connected devices."
    echo
    echo "To connect wirelessly:"
    echo "  1. Enable wireless debugging on the device"
    echo "  2. adb pair <ip>:<pair-port>    # enter pairing code"
    echo "  3. adb connect <ip>:<port>"
    exit 0
fi
DEVICES=("${(@f)output}")

echo "Connected devices (${#DEVICES[@]}):"
echo
for serial in "${DEVICES[@]}"; do
    model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null || echo "unknown")
    android=$(adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null || echo "?")
    echo "  $serial  —  $model (Android $android)"
done
