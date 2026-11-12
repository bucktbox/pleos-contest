#!/usr/bin/env bash
# Virtual Console HTTP API 로 S1 시연 흐름을 재현한다.
# 전제: 에뮬레이터 실행 중 + `adb -s emulator-5554 forward tcp:8088 tcp:8088`
set -u
VC=http://127.0.0.1:8088
put() { echo "[$(date +%H:%M:%S)] PUT $1"; curl -s -o /dev/null -w "  -> %{http_code}\n" -X PUT "$VC$1"; }
controls() { put "/vehicle/controls?speed=$1&accelerator=$2&brake=$3&steering=$4"; }

# VC 는 "브라우저 세션"이 살아 있을 때만 입력을 받는다 (timeout 3초) → 1초마다 heartbeat
CLIENT="poc-cli-$$"
( while true; do curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT"; sleep 1; done ) &
HB=$!
trap 'kill $HB 2>/dev/null; curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT&closing=1"' EXIT
sleep 1.5

echo "== 0. 탑승: 운전석 + 뒷좌석 2명"
put "/vehicle/seat?kind=occupied&area=driver&value=true"
put "/vehicle/seat?kind=occupied&area=rear_left&value=true"
put "/vehicle/seat?kind=occupied&area=rear_right&value=true"
sleep 2

echo "== 1. D 출발, 30km/h"
put "/vehicle/gear?gear=Drive"; sleep 1
controls 10 30 0 0; sleep 1
controls 30 30 0 0; sleep 3

echo "== 2. 깜빡이 먼저 → 1.5초 뒤 좌회전 (퍼펙트 시그널 후보)"
put "/vehicle/turn-signal?mode=BlinkLeft"; sleep 1.5
controls 30 20 0 -90; sleep 1
controls 30 20 0 -180; sleep 1
controls 30 20 0 0; put "/vehicle/turn-signal?mode=Off"; sleep 2

echo "== 3. 깜빡이 없이 우회전 (무깜빡이 조향 후보)"
controls 30 20 0 120; sleep 1
controls 30 20 0 0; sleep 2

echo "== 4. 급조향 (0 → 400도 한 번에)"
controls 30 0 0 400; sleep 0.5
controls 30 0 0 0; sleep 2

echo "== 5. 부드러운 감속 30 → 0 (5초)"
for v in 24 18 12 6 0; do controls $v 0 20 0; sleep 1; done
controls 0 0 0 0; sleep 2

echo "== 6. 다시 출발 후 급제동 40 → 0 (1초)"
controls 40 40 0 0; sleep 3
controls 0 0 100 0; sleep 1
controls 0 0 0 0; sleep 2

echo "== 7. 뒷좌석 한 명 하차"
put "/vehicle/seat?kind=occupied&area=rear_right&value=false"; sleep 2

echo "== 8. P 정차 (앱: 조명 Purple 3초 + TTS 자동 시험)"
put "/vehicle/gear?gear=Park"; sleep 6
echo "== 끝"
