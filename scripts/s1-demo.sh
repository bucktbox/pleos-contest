#!/usr/bin/env bash
# 세이프 카고 S1 시연 — Virtual Console(VC) HTTP API 로 「로비 → 호위 → 언박싱 → 리워드」 를 자동 진행한다.
#
# 사용법:  scripts/s1-demo.sh [dest] [--close-reward]
#   dest            Mock 목적지 id (cafe · rest · muni · roast, 기본 cafe)
#   --close-reward  이전 회차의 리워드 화면이 열려 있으면 [닫기] 를 대신 눌러 준다
#
# 전제: 에뮬레이터(emulator-5554) 실행 중, 앱(com.safecargo.app) 설치됨, VC 가 8088 포트에서 대기.
# 앱은 매 회차 force-stop 후 다시 띄운다 (상태는 앱이 디스크에서 복원 — 회차 사이 앱은 로비 상태여야 한다).
# 목적지 선택은 디버그 빌드의 `--es dest` 를 먼저 쓰고, 안 되면(릴리스 빌드 등) 화면을 눌러 고른다.
set -u

SERIAL=emulator-5554
USER_ID=10
PKG=com.safecargo.app
ACT="$PKG/.MainActivity"
VC=http://127.0.0.1:8088
DEST=cafe
CLOSE_REWARD=0
for a in "$@"; do
  case "$a" in
    --close-reward) CLOSE_REWARD=1 ;;
    -h|--help) sed -n 2,11p "$0"; exit 0 ;;
    -*) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
    *) DEST="$a" ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CATALOG="$ROOT/app/src/main/assets/mock/catalog.json"
ADB=(adb -s "$SERIAL")
WORK="$(mktemp -d)"

log() { echo "[$(date +%H:%M:%S)] $*"; }
die() { echo "[$(date +%H:%M:%S)] 실패: $*" >&2; exit 1; }

put() { log "PUT $1"; curl -s -o /dev/null -w "  -> %{http_code}\n" -X PUT "$VC$1"; }
controls() { put "/vehicle/controls?speed=$1&accelerator=$2&brake=$3&steering=$4"; }

# 앱 로그(SAFECARGO 태그)에서 패턴이 나올 때까지 기다린다. $1=정규식 $2=초
wait_log() {
  local pat="$1" sec="$2" i
  for ((i = 0; i < sec * 2; i++)); do
    if "${ADB[@]}" logcat -d -s SAFECARGO 2>/dev/null | grep -Eq -- "$pat"; then return 0; fi
    sleep 0.5
  done
  return 1
}

# 분할 화면(내비 등)에서는 uiautomator 가 포커스 창만 덤프한다 → 앱 창 모서리(빈 배경)를 한 번 눌러 포커스를 가져온다.
focus_app() {
  "${ADB[@]}" shell dumpsys window | grep -E "mCurrentFocus=.*$PKG/" -q && return 0
  local frame
  frame="$("${ADB[@]}" shell dumpsys window windows | grep -A40 "$PKG/$PKG.MainActivity}:" | grep -m1 -oE " frame=\[[0-9]+,[0-9]+\]" | grep -oE "[0-9]+,[0-9]+")"
  [ -n "$frame" ] || return 1
  "${ADB[@]}" shell input tap "$(( ${frame%,*} + 6 ))" "$(( ${frame#*,} + 6 ))"
  sleep 0.5
}

# 화면에서 글자가 정확히 같은 노드를 찾아 가운데를 누른다. $1=글자
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

# ---- 0. 준비 ----
"${ADB[@]}" get-state >/dev/null 2>&1 || die "$SERIAL 이 연결돼 있지 않아요"
[ -f "$CATALOG" ] || die "카탈로그가 없어요: app/src/main/assets/mock/catalog.json"

# 목적지 좌표 (경도 먼저 — emu geo fix 순서). 좌표는 화면에 출력하지 않는다.
GEO="$(python3 -I -c '
import json, sys
for d in json.load(open(sys.argv[1], encoding="utf-8"))["destinations"]:
    if d["id"] == sys.argv[2]:
        print(d["lng"], d["lat"])
        break
' "$CATALOG" "$DEST")"
[ -n "$GEO" ] || die "카탈로그에 목적지 '$DEST' 가 없어요 (cafe · rest · muni · roast)"
DEST_NAME="$(python3 -I -c '
import json, sys
print(next(d["name"] for d in json.load(open(sys.argv[1], encoding="utf-8"))["destinations"] if d["id"] == sys.argv[2]))
' "$CATALOG" "$DEST")"

"${ADB[@]}" forward tcp:8088 tcp:8088 >/dev/null || die "8088 포워딩 실패"

# VC 는 "브라우저 세션"이 살아 있을 때만 입력을 받는다 (timeout 3초) → 1초마다 heartbeat
CLIENT="s1-demo-$$"
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

log "== 0. 차량 초기화: P · 정지 · 깜빡이 끔 · 좌석 비움"
put "/vehicle/gear?gear=Park"
controls 0 0 0 0
put "/vehicle/turn-signal?mode=Off"
for area in driver passenger rear_left rear_right; do put "/vehicle/seat?kind=occupied&area=$area&value=false"; done
sleep 1

# 정차 시 위치 1회 확인용 런타임 권한 (이미 있으면 그대로)
"${ADB[@]}" shell pm grant --user "$USER_ID" "$PKG" pleos.car.permission.FUSED_LOCATION 2>/dev/null || true

log "== 1. 앱 다시 띄우기 + 목적지 '$DEST'"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -S -W --user "$USER_ID" -n "$ACT" --es dest "$DEST" >/dev/null || die "앱 실행 실패"

# 이전 회차 리워드가 남아 있으면(OPENING 복원) 목적지를 고를 수 없다.
sleep 2
if "${ADB[@]}" logcat -d -s SAFECARGO | grep -Eq "Restore : .* -> OPENING"; then
  if [ "$CLOSE_REWARD" = 1 ]; then
    tap_text "닫기" || die "리워드 [닫기] 를 찾지 못했어요"
    sleep 1
    "${ADB[@]}" logcat -c
    "${ADB[@]}" shell am start -S -W --user "$USER_ID" -n "$ACT" --es dest "$DEST" >/dev/null || die "앱 실행 실패"
  else
    die "이전 회차 리워드 화면이 열려 있어요 — [닫기] 를 누른 뒤 다시 실행하세요 (또는 --close-reward)"
  fi
fi

SEL="SelectDestination\\($DEST\\) : (IDLE|LOADED) -> LOADED"
if ! wait_log "$SEL" 6; then
  log "디버그 인텐트로 고르지 못함 — 화면에서 고릅니다"
  tap_text "목적지 고르기" || tap_text "변경" || die "로비의 [목적지 고르기] 를 찾지 못했어요 (앱이 로비인지 확인)"
  sleep 1.5
  tap_text "$DEST_NAME" || die "목적지 「$DEST_NAME」 를 화면에서 찾지 못했어요"
  sleep 1
  # 카드를 누르면 미리보기만 바뀐다 — 확정은 [이 목적지로 안내] (11/03 SC-02).
  tap_text "이 목적지로 안내" || die "[이 목적지로 안내] 버튼을 찾지 못했어요"
  wait_log "$SEL" 8 || die "목적지 선택 로그(SelectDestination($DEST) -> LOADED)가 나오지 않았어요"
fi
log "목적지 선택 확인 (LOADED)"
sleep 2

log "== 2. 탑승: 운전석 + 뒷좌석 1명"
put "/vehicle/seat?kind=occupied&area=driver&value=true"
put "/vehicle/seat?kind=occupied&area=rear_left&value=true"
sleep 2

log "== 3. D 출발, 30km/h"
put "/vehicle/gear?gear=Drive"; sleep 1
controls 10 30 0 0; sleep 1
controls 30 30 0 0; sleep 3
wait_log "-> ESCORTING" 5 || die "호위(ESCORTING)로 넘어가지 않았어요"

log "== 4. 깜빡이 좌 → 1.5초 뒤 좌회전"
put "/vehicle/turn-signal?mode=BlinkLeft"; sleep 1.5
controls 30 20 0 -90; sleep 1
controls 30 20 0 -180; sleep 1
controls 30 20 0 0; put "/vehicle/turn-signal?mode=Off"; sleep 2

log "== 5. 깜빡이 우 → 1초 뒤 우회전"
put "/vehicle/turn-signal?mode=BlinkRight"; sleep 1
controls 30 20 0 120; sleep 1
controls 30 20 0 0; put "/vehicle/turn-signal?mode=Off"; sleep 2

log "== 6. 부드러운 감속 30 → 0"
for v in 24 18 12 6 0; do controls $v 0 20 0; sleep 1; done
controls 0 0 0 0; sleep 1

log "== 7. 목적지 위치로 이동 (emu geo fix)"
# shellcheck disable=SC2086
"${ADB[@]}" emu geo fix $GEO >/dev/null || die "emu geo fix 실패"
sleep 2

log "== 8. P 정차 → 언박싱"
put "/vehicle/gear?gear=Park"
sleep 10

# ---- 결과 ----
OUT="$("${ADB[@]}" logcat -d -v threadtime -s SAFECARGO)"
P_LINE="$(grep -E "GearChanged : ESCORTING -> PAUSED" <<<"$OUT" | tail -1)"
O_LINE="$(grep -E -- "-> OPENING .*rewardId=" <<<"$OUT" | tail -1)"
[ -n "$O_LINE" ] || die "언박싱(OPENING)으로 넘어가지 않았어요"
grep -Eq "rewardId=coupon-$DEST" <<<"$O_LINE" || log "주의: 도착 쿠폰이 아니에요 → $(sed 's/.*rewardId=/rewardId=/' <<<"$O_LINE")"
if [ -n "$P_LINE" ]; then
  MS="$(python3 -I -c '
import sys
def ms(line):
    h, m, s = line.split()[1].split(":")
    return (int(h) * 3600 + int(m) * 60) * 1000 + round(float(s) * 1000)
print(ms(sys.argv[2]) - ms(sys.argv[1]))
' "$P_LINE" "$O_LINE")"
  log "P → 언박싱 ${MS}ms"
fi
log "완료: $(sed 's/.*SAFECARGO *: //' <<<"$O_LINE")"
