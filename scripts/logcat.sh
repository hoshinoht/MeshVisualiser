#!/bin/zsh
# Stream logcat from all connected Android devices in parallel
# Each device's output is prefixed with its serial for easy identification
# Usage:
#   ./scripts/logcat.sh              # all logs
#   ./scripts/logcat.sh --app        # only this app's logs
#   ./scripts/logcat.sh --tag "TAG"  # filter by tag
set -euo pipefail

PACKAGE="com.example.meshvisualiser"
APP_ONLY=false
TAG_FILTER=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --app)
            APP_ONLY=true
            shift
            ;;
        --tag)
            TAG_FILTER="$2"
            shift 2
            ;;
        --clear)
            # Clear logcat on all devices first
            DEVICES=("${(@f)$(adb devices | awk 'NR>1 && /device$/ {print $1}')}")
            for serial in "${DEVICES[@]}"; do
                adb -s "$serial" logcat -c
            done
            echo "Cleared logcat on ${#DEVICES[@]} device(s)"
            shift
            ;;
        *)
            echo "Usage: $0 [--app] [--tag TAG] [--clear]"
            exit 1
            ;;
    esac
done

output=$(adb devices | awk 'NR>1 && /device$/ {print $1}')
if [[ -z "$output" ]]; then
    echo "ERROR: No connected devices found"
    exit 1
fi
DEVICES=("${(@f)output}")

# Assign colors for each device (ANSI codes)
COLORS=(31 32 33 34 35 36 91 92 93 94 95 96)

echo "Streaming logcat from ${#DEVICES[@]} device(s). Ctrl+C to stop."
echo "---"

cleanup() {
    kill 0 2>/dev/null
    wait 2>/dev/null
    echo -e "\nStopped."
}
trap cleanup EXIT INT TERM

for i in {1..${#DEVICES[@]}}; do
    serial="${DEVICES[$i]}"
    color="${COLORS[$(( (i - 1) % ${#COLORS[@]} + 1 ))]}"

    # Build logcat command
    CMD=(adb -s "$serial" logcat)

    if $APP_ONLY; then
        PID=$(adb -s "$serial" shell pidof "$PACKAGE" 2>/dev/null || true)
        if [[ -n "$PID" ]]; then
            CMD+=(--pid="$PID")
        else
            echo -e "\033[${color}m[$serial]\033[0m WARNING: $PACKAGE not running, showing all logs"
        fi
    fi

    if [[ -n "$TAG_FILTER" ]]; then
        CMD+=("$TAG_FILTER:V" "*:S")
    fi

    # Prefix each line with colored device serial
    "${CMD[@]}" 2>&1 | while IFS= read -r line; do
        echo -e "\033[${color}m[$serial]\033[0m $line"
    done &
done

wait
