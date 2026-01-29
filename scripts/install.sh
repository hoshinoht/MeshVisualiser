#!/bin/zsh
# Install debug APK on all connected Android devices (parallel)
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# Build first if --build flag or APK doesn't exist
if [[ "${1:-}" == "--build" ]] || [[ ! -f "$APK" ]]; then
    echo "Building debug APK..."
    ./gradlew assembleDebug
fi

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK not found at $APK"
    echo "Run with --build flag or run ./gradlew assembleDebug first"
    exit 1
fi

# Get connected device serials
output=$(adb devices | awk 'NR>1 && /device$/ {print $1}')
if [[ -z "$output" ]]; then
    echo "ERROR: No connected devices found"
    echo "Connect devices via 'adb connect <ip>:<port>' first"
    exit 1
fi
DEVICES=("${(@f)output}")

echo "Found ${#DEVICES[@]} device(s): ${DEVICES[*]}"
echo "Installing $APK..."
echo

FAIL=0
for serial in "${DEVICES[@]}"; do
    (
        echo "[$serial] Installing..."
        if adb -s "$serial" install -r "$APK" 2>&1; then
            echo "[$serial] Done"
        else
            echo "[$serial] FAILED"
        fi
    ) &
done
wait

echo
echo "Install complete."
