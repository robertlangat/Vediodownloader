#!/usr/bin/env bash
# Runs the instrumented tests on the booted emulator and collects logs + screenshots.
set -u
adb logcat -c || true
adb logcat -v time -s SVD-TEST:V SVD-LIVE:V SVD-EXTRACT:D AndroidRuntime:E > svd-test-logcat.txt 2>/dev/null &
LOGPID=$!
./gradlew connectedDebugAndroidTest > emulator-log.txt 2>&1
status=$?
sleep 2; kill $LOGPID 2>/dev/null || true
tail -n 40 emulator-log.txt
if [ $status -ne 0 ]; then touch emulator-failed; fi
mkdir -p screenshots
adb pull /sdcard/Pictures/SVD-test-screenshots screenshots/ >/dev/null 2>&1 || true
exit 0
