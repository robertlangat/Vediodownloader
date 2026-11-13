#!/usr/bin/env bash
# Runs the instrumented tests on the booted emulator and collects logs + screenshots.
set -u
adb logcat -c || true
./gradlew connectedDebugAndroidTest -PbackendUrl="${BACKEND_URL:-}" > emulator-log.txt 2>&1
status=$?
tail -n 40 emulator-log.txt
if [ $status -ne 0 ]; then touch emulator-failed; fi
adb logcat -d -s SVD-TEST:V > svd-test-logcat.txt 2>/dev/null || true
mkdir -p screenshots
adb pull /sdcard/Pictures/SVD-test-screenshots screenshots/ >/dev/null 2>&1 || true
exit 0
