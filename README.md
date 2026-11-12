# 세이프 카고

Pleos Connect(Android Automotive)용 공모전 앱. 차에 「카고」를 싣고 깜빡이 · 부드러운 조향으로 무사히 모셔 가면,
정차(P) 순간 상자가 열리고 열쇠 · 등급 · 보상이 나온다. 주행 중 화면은 숫자 · 아이콘만 보여 주고 조작할 것이 없다.

- 패키지 `com.safecargo.app` · minSdk 28 · targetSdk 34 · compileSdk 36 · JDK 17
- 모듈 `:app` 하나 (`engine/` 은 Android 의존 없는 판정 엔진, `sdk/` 는 Pleos SDK 래퍼, `ui/` 는 Compose 화면)
- `poc/` 는 SDK 검증용 별도 앱이다. 이 앱의 빌드와 무관하다.

## APK

- 저장소: [`apk/현실도피_세이프카고_release.apk`](apk/현실도피_세이프카고_release.apk) (실행 가능한 release 빌드)
- GitHub Release: <https://github.com/bucktbox/pleos-contest/releases/tag/v1.1-demo-20261111> — 같은 APK(`safecargo_v1.1_release.apk`)와 시연 영상 2개

## 테스트 환경

- Pleos Connect Emulator **2.1.0** · Virtual Console(VC) 2.1.0 · Android API 34 이미지 · 매 실행 cold boot
- 화면 규격: 기준 화면 S-17 **가로 1700×1184dp** · 세로 840×1184dp. Pleos Connect 디스플레이 6종 · 앱 창 12개(가장 작은 창 628×800dp)에 맞춰 레이아웃이 바뀐다.
- SDK: Vehicle 2.3.2 · NaviHelper 3.0.5 · TextToSpeech 3.3.1 · FusedLocation 1.1.3
- 차량 신호는 VC 로 재현한다. 실차에서는 확인하지 않았다.

## 빌드

```bash
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest  # 엔진 · 매핑 단위 테스트
```

Pleos SDK 는 `settings.gradle.kts` 의 Pleos Playground Maven 저장소에서 받는다.

> **release 서명**: 공모전 제출본 release APK 는 에뮬레이터 설치 · 시연용으로 debug 키로 서명했다(`app/build.gradle.kts`). 앱 마켓 배포 단계에서 정식 키로 교체한다.
> 키스토어 · 비밀번호는 저장소에 넣지 않는다(`.gitignore` 에 `*.jks` · `keystore.properties`).

## 설치 (에뮬레이터)

Pleos Playground 에뮬레이터 기준. 앞에 보이는 Android 사용자는 10 이다.

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# 위치 권한(정차 시 도착 확인용) — 앱 로비의 권한 카드에서 허용해도 된다
adb -s emulator-5554 shell pm grant --user 10 com.safecargo.app pleos.car.permission.FUSED_LOCATION
adb -s emulator-5554 shell am start --user 10 -n com.safecargo.app/.MainActivity
```

## 시연 (S1)

`scripts/s1-demo.sh` 가 Virtual Console(VC) HTTP API 로 차량을 움직여 「로비 → 호위 → 언박싱 → 리워드」 를 자동으로 진행한다.

```bash
scripts/s1-demo.sh            # 목적지 cafe (베이커리 카페 예시)
scripts/s1-demo.sh rest       # muni · rest · cafe 중 하나
scripts/s1-demo.sh --close-reward   # 이전 회차 리워드가 열려 있으면 [닫기] 를 대신 누름
```

흐름: VC 포트 포워딩(8088) · heartbeat → 차량 초기화(P · 정지 · 좌석 비움) → 앱 재시작 + 목적지 선택 →
탑승(운전석 + 뒷좌석 1) → D 30km/h → 깜빡이 좌 1.5초 뒤 좌회전 → 깜빡이 우 1초 뒤 우회전 → 감속 →
목적지 위치로 이동(`emu geo fix`) → P → 10초 대기. 끝에 P → 언박싱 전환 시간(앱 로그 기준)을 출력한다.

- 회차 사이에는 리워드 화면의 **[닫기]** 만 누르면 된다. 앱은 매 회차 다시 띄우며, 진행 상태는 앱이 기기에서 복원한다.
- 목적지는 디버그 빌드의 `--es dest <id>` 로 고르고, 안 되면(release 빌드 등) 로비 화면을 눌러 고른다.
- 앱 로그는 `adb -s emulator-5554 logcat -s SAFECARGO` 로 본다. 로그에는 단계 전이 · 열쇠 수만 남고 좌표는 남지 않는다.
- 디버그 빌드에는 로비에 [기록 초기화] 가 있다(무사 배송 기록 · 진행 상태 삭제). release 에는 없다.

## 권한

`AndroidManifest.xml` 에 선언한 권한은 아래 10개뿐이다. 위치 · 차속만 런타임 요청이다. 위치는 앱 시작 때 자동으로 묻지 않고 로비 권한 카드에서만 요청하며, 차속은 첫 실행 때 한 번만 묻는다.

| 권한 | 용도 |
| :--- | :--- |
| `pleos.car.permission.CAR_DRIVE` | 기어 · 조향 (호위 시작 · 정차 · 부드러운 조향 판정) |
| `pleos.car.permission.CAR_LIGHTS` | 깜빡이 상태 · 실내 조명 읽기 |
| `pleos.car.permission.CONTROL_CAR_LIGHTS` | 개봉 순간 실내 조명을 등급 색으로 3초 바꿨다가 원래대로 |
| `pleos.car.permission.CAR_SEATS` | 착석 (패밀리 버프) · 벨트 |
| `pleos.car.permission.NAVI_ROUTE` | 내비 상태 · 경로 이벤트 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | 고른 목적지로 내비 경로 요청 |
| `pleos.car.permission.TTS_SERVICE` | 도착 안내 음성 (정차 후에만) |
| `pleos.car.permission.FUSED_LOCATION` (런타임) | 정차 시 위치 1회 — 목적지 도착 확인 |
| `android.car.permission.CAR_DRIVING_STATE` | 주행 중 UX 제한 상태 확인 (주행 중 조작 차단) |
| `android.car.permission.CAR_SPEED` (런타임) | 차속 — 급가속 · 급정거 모션 감지에만 사용, 저장하지 않음. 거부해도 앱은 동작한다 |

Pleos SDK · AndroidX 라이브러리가 빌드 때 자체 권한(서비스 바인딩 · 인터넷 · 동적 리시버)을 병합한다.
최종 APK 목록은 `aapt dump permissions app/build/outputs/apk/release/app-release.apk` 로 확인한다.

## 위치 원칙

- 위치는 **P 로 정차한 순간 1회만** 읽어 목적지 반경 안인지 확인한다. 주행 중에는 읽지 않는다.
- **사용자 위치 좌표는 저장하지 않는다** (Mock 목적지 좌표만 앱 안에 있음). 어디에도 전송하지 않고 로그에도 남기지 않는다. 결과로는 「도착 인증됨 / 없음」 만 남는다.
- 권한을 주지 않아도 앱은 동작한다. 그때는 도착 확인 없이 진행되고 보상에 「도착 인증 없음」 이 표시된다.

## 착석 신호 (확인 필요)

- Vehicle SDK 2.3.2 의 착석 Boolean 은 에뮬레이터에서 **빈 좌석(VACANT)일 때 true** 를 돌려준다.
  앱은 `config/judge.json` 의 `seatOccupancyInverted: true` 로 이를 뒤집어 쓴다.
- 이 보정의 한계: SDK Boolean 으로는 UNKNOWN 과 OCCUPIED 를 구분할 수 없어, 뒤집으면 **UNKNOWN 이 「착석」 으로 읽힌다.**
- 실차에서 동작을 확인하고, SDK 가 고쳐져 있으면 `seatOccupancyInverted` 를 `false` 로 둔다.

## Mock · 「예시」 고지

- 목적지 · 스폰서 · 쿠폰 · 금액은 모두 시연용 Mock 데이터다(`app/src/main/assets/mock/catalog.json`). 실제 업체 · 제휴와 관계없다.
- 화면의 금액 · 제휴명 · 스폰서명 옆에는 「예시」 배지를 붙였고, 리워드 화면에는 「시연용 가상 보상 · 실제 사용 불가」 를 표시한다.
- 앱은 자체 서버가 없고 사용자 데이터를 밖으로 보내지 않는다. 기록(무사 배송 일수 등)은 기기 안에만 저장한다.

## 글꼴

Pretendard 를 사용한다. SIL Open Font License 1.1 에 따라 배포되며, 저작권 고지와 라이선스 전문은
`app/src/main/assets/licenses/pretendard-OFL.txt` 에 있다.
