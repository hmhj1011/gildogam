#!/usr/bin/env bash
# Installs the debug APK on a running emulator, drives the demo console and records the screen.
# Used by .github/workflows/demo-run.yml. Output: demo-out/ (screenshots, video, logcat).
set -uo pipefail

OUT=demo-out
mkdir -p "$OUT"
APK="$(ls app/build/outputs/apk/debug/*.apk | head -n 1)"
PKG=kr.gildogam

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

# Tap the on-screen element whose text is exactly $1 (Compose exposes text to UI Automator).
tap() {
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
    xy="$(python3 scripts/find_text.py "$OUT/ui.xml" "$1")"
    if [ -n "$xy" ]; then
      adb shell input tap $xy
      echo "tap '$1' at $xy"
      return 0
    fi
    sleep 2
  done
  echo "NOT FOUND: '$1'"
  cp "$OUT/ui.xml" "$OUT/ui-missing-$(date +%s).xml"
  return 1
}

adb logcat -c
adb logcat -v time > "$OUT/logcat.txt" 2>&1 &
LOGCAT_PID=$!

# A wide in-car display: 1920x1080 landscape.
adb shell wm size 1920x1080
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell service call alarm 3 s16 Asia/Seoul >/dev/null 2>&1 || true
adb shell setprop persist.sys.timezone Asia/Seoul

adb install -r -g "$APK"
adb shell am start -n "$PKG/.MainActivity"
sleep 12
shot 01-start

tap "시연 콘솔"
sleep 2
shot 02-console

# Trip 1: a clear day at noon. Record it (screenrecord stops by itself at 170 s).
tap "낮 12시"
sleep 1
adb shell screenrecord --time-limit 170 --bit-rate 2000000 /sdcard/trip1.mp4 &
REC_PID=$!
tap "서울 → 강릉 경포해변"
for i in $(seq -w 1 14); do
  sleep 11
  shot "03-trip1-$i"
done
wait $REC_PID
adb pull /sdcard/trip1.mp4 "$OUT/trip1.mp4" >/dev/null 2>&1
sleep 8
shot 04-arrived

# Trip 2: the same road on a rainy night.
tap "밤 10시"
sleep 1
tap "와이퍼 켬"
sleep 1
tap "서울 → 강릉 경포해변"
for i in $(seq -w 1 14); do
  sleep 11
  shot "05-trip2-$i"
done
sleep 10
tap "와이퍼 끔"
sleep 1
shot 06-arrived

# The journey book (opens only while parked).
tap "시연 콘솔 닫기"
sleep 2
tap "여정 도감"
sleep 3
shot 07-pages
tap "강릉 경포해변 가는 길" || true
sleep 2
shot 08-page-detail
tap "카드 도감"
sleep 3
shot 09-cards
tap "대관령" || true
sleep 2
shot 10-card-daegwallyeong

kill $LOGCAT_PID 2>/dev/null || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::warning::The app crashed during the demo run; see logcat.txt"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
fi
ls -la "$OUT"
exit 0
