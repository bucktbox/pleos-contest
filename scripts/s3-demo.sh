#!/usr/bin/env bash
# 세이프 카고 S3 시연 — 「험하게 운전한 날」: 데일리 카고 → 급가속 → 깜빡이 없는 급조향 → 급정거 → 정차 → 아쉬운 상자.
# (PRD v1.1 2.2 S3 · FR-VH-014 · 화면정의서 v1.1 전달메모 개발 당부)
#
# 사용법:  scripts/s3-demo.sh [--reset] [--close-reward]
#   --reset         보상함을 비우고 시연 초기값(쿠폰 4장 · 토큰 7)부터 다시
#   --close-reward  이전 회차의 리워드 화면이 열려 있으면 [닫기] 를 대신 눌러 준다
#
# 전제: 에뮬레이터(emulator-5554) 실행 중, 디버그 앱(com.safecargo.app) 설치됨, VC 가 8088 포트에서 대기.
# 시연 모드는 디버그 인텐트 `--ez demo true` 로 켠다 — 레어 등급이면 꽝(아쉬운 상자)이 나오도록 추첨이 고정된다.
# 급가속 · 급정거 모션은 차속 권한(CAR_SPEED)이 있어야 나온다. 없으면 흔들림만 (EX-09).
set -u

SERIAL=emulator-5554
USER_ID=10
PKG=com.safecargo.app
ACT="$PKG/.MainActivity"
VC=http://127.0.0.1:8088
RESET=false
CLOSE_REWARD=0
for a in "$@"; do
  case "$a" in
    --reset) RESET=true ;;
    --close-reward) CLOSE_REWARD=1 ;;
    -h|--help) sed -n 2,12p "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
  esac
done

ADB=(adb -s "$SERIAL")
WORK="$(mktemp -d)"

log() { echo "[$(date +%H:%M:%S)] $*"; }
die() { echo "[$(date +%H:%M:%S)] 실패: $*" >&2; exit 1; }
put() { log "PUT $1"; curl -s -o /dev/null -w "  -> %{http_code}\n" -X PUT "$VC$1"; }
controls() { put "/vehicle/controls?speed=$1&accelerator=$2&brake=$3&steering=$4"; }

wait_log() {
  local pat="$1" sec="$2" i
  for ((i = 0; i < sec * 2; i++)); do
    if "${ADB[@]}" logcat -d -s SAFECARGO 2>/dev/null | grep -Eq -- "$pat"; then return 0; fi
    sleep 0.5
  done
  return 1
}

focus_app() {
  "${ADB[@]}" shell dumpsys window | grep -E "mCurrentFocus=.*$PKG/" -q && return 0
  local frame
  frame="$("${ADB[@]}" shell dumpsys window windows | grep -A40 "$PKG/$PKG.MainActivity}:" | grep -m1 -oE " frame=\[[0-9]+,[0-9]+\]" | grep -oE "[0-9]+,[0-9]+")"
  [ -n "$frame" ] || return 1
  "${ADB[@]}" shell input tap "$(( ${frame%,*} + 6 ))" "$(( ${frame#*,} + 6 ))"
  sleep 0.5
}

tap_text() {
  focus_app
  "${ADB[@]}" shell uiautomator dump /data/local/tmp/sc-ui.xml >/dev/null 2>&1 || return 1
  "${ADB[@]}" exec-out cat /data/local/tmp/sc-ui.xml >"$WORK/ui.xml" 2>/dev/null || return 1
  local xy
  xy="$(python3 -I -c '
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[2]
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("text") == want:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
' "$WORK/ui.xml" "$1" 2>/dev/null)"
  [ -n "$xy" ] || return 1
  log "TAP 「$1」"
  # shellcheck disable=SC2086
  "${ADB[@]}" shell input tap $xy
}

"${ADB[@]}" get-state >/dev/null 2>&1 || die "$SERIAL 이 연결돼 있지 않아요"
"${ADB[@]}" forward tcp:8088 tcp:8088 >/dev/null || die "8088 포워딩 실패"

CLIENT="s3-demo-$$"
( while true; do curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT"; sleep 1; done ) &
HB=$!
cleanup() {
  kill "$HB" 2>/dev/null
  wait "$HB" 2>/dev/null
  curl -s -o /dev/null -X POST "$VC/browser/session?client=$CLIENT&closing=1"
  rm -rf "$WORK"
}
trap cleanup EXIT
sleep 1.5
curl -s -o /dev/null --max-time 3 "$VC/" || die "VC($VC) 에 연결할 수 없어요"

log "== 0. 차량 초기화: P · 정지 · 깜빡이 끔 · 운전석만"
put "/vehicle/gear?gear=Park"
controls 0 0 0 0
put "/vehicle/turn-signal?mode=Off"
for area in passenger rear_left rear_right; do put "/vehicle/seat?kind=occupied&area=$area&value=false"; done
put "/vehicle/seat?kind=occupied&area=driver&value=true"
sleep 1

"${ADB[@]}" shell pm grant --user "$USER_ID" "$PKG" pleos.car.permission.FUSED_LOCATION 2>/dev/null || true
"${ADB[@]}" shell pm grant --user "$USER_ID" "$PKG" android.car.permission.CAR_SPEED 2>/dev/null \
  || log "주의: CAR_SPEED 권한을 줄 수 없어요 — 급가속 · 급정거 모션 없이 흔들림만"

log "== 1. 앱 다시 띄우기 (시연 모드${RESET:+, reset=$RESET})"
"${ADB[@]}" logcat -c
start_app() { "${ADB[@]}" shell am start -S -W --user "$USER_ID" -n "$ACT" --ez demo true --ez demoReset "$RESET" >/dev/null; }
start_app || die "앱 실행 실패"
sleep 2
if "${ADB[@]}" logcat -d -s SAFECARGO | grep -Eq "Restore : .* -> OPENING"; then
  [ "$CLOSE_REWARD" = 1 ] || die "이전 회차 리워드 화면이 열려 있어요 — [닫기] 를 누른 뒤 다시 실행하세요 (또는 --close-reward)"
  tap_text "닫기" || die "리워드 [닫기] 를 찾지 못했어요"
  sleep 1
  "${ADB[@]}" logcat -c
  RESET=false start_app || die "앱 실행 실패"
fi
wait_log "demo on=true" 6 || die "시연 모드 로그(demo on=true)가 나오지 않았어요 (디버그 빌드인지 확인)"
sleep 1

log "== 2. D 출발 (데일리 카고 자동 적재)"
put "/vehicle/gear?gear=Drive"; sleep 1
controls 5 10 0 0; sleep 2
wait_log "-> ESCORTING" 5 || die "호위(ESCORTING)로 넘어가지 않았어요"
controls 5 10 0 0; sleep 2   # 차속 창(1초)을 채운다

log "== 3. 급가속 5 → 40km/h (+9.7 m/s, 1초)"
controls 40 100 0 0; sleep 3

log "== 4. 깜빡이 없이 급조향 (0 → -300°)"
controls 40 20 0 -300; sleep 1
controls 40 20 0 0; sleep 3

log "== 5. 급정거 40 → 0km/h"
controls 0 0 100 0; sleep 2
controls 0 0 0 0; sleep 1

log "== 6. P 정차 → [도착 · 개봉]"
put "/vehicle/gear?gear=Park"
sleep 3
tap_text "도착 · 개봉" || die "[도착 · 개봉] 을 찾지 못했어요"
sleep 1
tap_text "열기" || die "확인 창의 [열기] 를 찾지 못했어요"
wait_log "-> OPENING .*rewardId=" 10 || die "언박싱(OPENING)으로 넘어가지 않았어요"
sleep 8

OUT="$("${ADB[@]}" logcat -d -s SAFECARGO)"
for m in ACCEL SHAKE BRAKE; do
  if grep -q "motion $m" <<<"$OUT"; then log "모션 $m ✓"; else log "모션 $m 없음"; fi
done
O_LINE="$(grep -E -- "-> OPENING .*rewardId=" <<<"$OUT" | tail -1)"
grep -q "rewardId=empty" <<<"$O_LINE" || log "주의: 아쉬운 상자가 아니에요 (레어가 아니면 시연 고정이 쿠폰 · 포인트를 줍니다)"
log "완료: $(sed 's/.*SAFECARGO *: //' <<<"$O_LINE")"
