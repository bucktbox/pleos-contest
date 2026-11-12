> SRC-04 · 기타 (개발 규정 — 공식 SDK 문서) · 「Pleos Connect SDK (pleos-only)」 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ · 읽은 날 26.10.29 · 작성 주체 Pleos (현대자동차그룹)
> 방법: 가이드 51쪽 전부 + 계획에 쓰는 API Reference 약 50쪽 (JS 번들 안 Dokka 원문) + 링크된 개발 환경 가이드 6쪽. 사이트맵의 나머지 API Reference(HVAC · Door 등 무관 도메인 약 1,800쪽)는 읽지 않았다.
> 표기: CONFIRMED = 문서에 있음 · REFUTED = 문서가 계획과 반대 · NOT STATED = 문서에 없음. 원문 인용은 「 」. URL 접두어 `…/` = https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/

## 1. 개발 환경 — CONFIRMED

- 「Minimum Android SDK: Android API 28 이상 / Gradle 8.0 이상 / Java, Kotlin」 · minSdk 28 은 차량 사이버 보안 요건(사이닝 키 로테이션 · 리니지) 때문.
- Android Studio 최신 버전 · Pleos Connect Emulator **2.1.0** (릴리스 2026-10-19) · Virtual Console 지원 최소 버전 2.1.0 · 시스템 이미지 API 34 · RAM 4GB · **Cold boot 필수** (「quick boot 시 VHAL 이용 불가」) · GPU 최소 RTX 3060 Laptop 6GB. LLM SDK 제공 종료.
- targetSdk / compileSdk — NOT STATED.
- 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`
- 의존성: `ai.pleos.playground:Vehicle:2.3.2` (Vehicle Service v2.3.21 기준) · `NaviHelper:3.0.5` · `TextToSpeech:3.3.1` · `SpeechToText:3.3.1` · `FusedLocation:1.1.3`
- URL: /docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator · …configure-pleos-connect-emulator · /docs/pleos-only/release-note/emulator-release-note · …/Vehicle/project-setup · 각 SDK importing-sdk / project-setup

## 2. Vehicle SDK 메서드 (v2.3.2)

모든 get / set / check 는 IPC 비동기 (`onComplete` / `onFailed`), 리스너마다 `onFailed(e)`.

| 계획의 호출 | 상태 | 문서 내용 |
|---|---|---|
| `getGear().registerGearSelection` | **REFUTED** | `Vehicle` 멤버에 `getGear()` 없음 · Gear 도메인 없음. 도메인 19개: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper. `Vehicle` 클래스 KDoc 예제에만 `getGear()` 가 남아 있음(갱신 안 된 예제로 보임). Virtual Console 에는 「Gear Selection (P/R/N/D)」 카드 있음 |
| `getTurnSignal().registerTurnSignalState` | CONFIRMED | `TurnSignalValue {OFF, RIGHT, LEFT}` · 퍼미션 CAR_LIGHTS |
| `getSteeringWheel().registerSteeringWheelAngle` | CONFIRMED · **뜻이 다름** | `Float?`, 단위 ° — 「front-wheel steering angle … leftward negative. **The reported value does not represent the rotation angle of the steering wheel**」. -540~540 은 Virtual Console 카드 설명에만 있음 · CAR_DRIVE |
| `getBrake().registerBrakePedalStatus` | CONFIRMED | `onBrakePedalStatusUpdated(status: Boolean?)` 「true 면 페달 밟힘」 · CAR_DRIVE |
| `getSeat().registerSeatOccupancy` / `registerSeatBeltBuckledState` / `getCurrentSeatOccupancy` / `getCurrentSeatBeltBuckledState` | CONFIRMED | 인자 `area: SeatArea`, 값 `Boolean?`, `SeatValue ROW_1_LEFT … ROW_3_RIGHT` · 퍼미션 **CAR_SEATS** (Safety 도메인 `hasSeatBeltNotBuckledWarning` 만 CAR_SAFETY) |
| `getOdometer().registerTripCurrentDrivingDistance` | CONFIRMED | `onDistanceDrivenUpdated(Float?)`, 단위 **m**, 「current driving session」 (`registerDistanceDriven` 은 km) · CAR_DRIVE |
| `getLight().get/setAmbientLightBrightnessColor` · `setAmbientLightBrightness` · `checkAmbientLightBrightnessColorCapabilities` | CONFIRMED | 좌석 영역(SeatArea) 단위 · 색 값 「32-bit ARGB, Alpha fixed 0xFF」 · 밝기 범위는 `checkAmbientLightBrightnessCapabilities` → `Map<SeatArea, AreaBounds<Int>>` · 원복 방법 NOT STATED (get 으로 저장 → set 으로 복원) |
| `vehicle.release()` | CONFIRMED | Application 종료 시점. 「비동기 콜백이 끝나기 전에 release() 호출 금지」 · 리스너 해제 후 release · release 뒤엔 객체 재생성 |

- 콜백 주기 — **NOT STATED**. 「등록한 값이 변경되면 onXxxUpdated」만 있음 (…/Vehicle/callback).
- 차량 속도 — **없음**. 크루즈 목표속도 · `Display.getCurrentSpeedDisplayUnit`(단위)만.
- Capability: `checkXxxCapabilities` → `CapabilityConfig.accessType` (NONE/READ/WRITE/READ_WRITE) + `AreaBounds` 또는 `List<SeatArea>`. 미지원이면 「disable UI or use an alternate flow」 · `ERROR_NOT_SUPPORTED_PROPERTY/AREA` 면 UI 비활성 (…/Vehicle/check-capability · vehicle-error-code).

## 3. 퍼미션 — CONFIRMED (문서끼리 한 곳 어긋남)

- 형식 `pleos.car.permission.<상수>`, protection level normal.
- 앰비언트 set 의 API Reference 는 `@RequiresPermission CAR_LIGHTS` — 반면 Permission 가이드는 「CONTROL_CAR_* = 도메인 제어(set)」. **문서끼리 다름.**
- 「퍼미션은 Pleos App Market 과 사전 협의 및 승인된 범위 내에서만 사용 … 승인되지 않은 퍼미션을 선언하거나 API 를 호출하면 앱 심사 통과 불가」 (…/ Overview · Vehicle/vehicle-permission).
- 기타: NAVI_ROUTE / NAVI_ROUTE_SEARCH / NAVI_CUSTOM_MAP · TTS_SERVICE · STT_SERVICE · FUSED_LOCATION (**dangerous — 런타임 요청 필요**).

## 4. NaviHelper 3.0.5 — CONFIRMED

- 목적지 설정 `requestRoute(RouteInfo)` · 경유지 `addWaypoint` / `requestRouteWithWaypoints` (최대 2개, FIRST/SECOND).
- **순정 내비 목적지 수신 가능**: `onDestinationInfo(DestinationInfo)` 「Called when the destination information is changed」 — lat/lng/name/address/remainDistance/remainTime/tollFare/poiId. `getDestinationInfo()` 결과도 이 이벤트로 옴. 단 「실제 전달 이벤트는 Navigation 앱 구현·상태에 따라 달라질 수 있음」.
- `onCurrentRoadSpeedLimit` (제한속도)만 있고 차속 없음.
- URL: …/NaviHelper/intro · destinations · event-listeners · waypoint-settings · …navi.data/destination-info

## 5. TextToSpeech 3.3.1 · SpeechToText 3.3.1 — CONFIRMED

- TTS: `TextToSpeech(context, Mode)` → `initialize()` → `addEventListener` → `speak(text, isFinal=true)` / `stop()`. 모드 HYBRID(기본) / SERVER / ON_DEVICE — **HYBRID · SERVER 는 Playground My Project Client ID/Secret 으로 `registerApp` 필요**. 로케일 ko-KR 등 5종. 주행 중 제한 — NOT STATED.
- STT: `request()` / `stop()`, 명령 실행은 하지 않음. (계획에서 미사용)

## 6. FusedLocation 1.1.3 — CONFIRMED

- 사용 전 `isSupported()` 필수 · 1회 조회 `getLastKnownLocation(cb)` (null 가능) · 연속 `register/unregisterFusedLocationCallback` · `release()`.
- `LocationData` = latitude, longitude, altitude, heading, confidence, timestampMillis — **속도 필드 없음 (REFUTED)**.
- 「ACCESS_FINE_LOCATION 등 Android 위치 권한 동작은 보장하지 않음」.

## 7. 주행 제한 (DO) — 이 섹션엔 오류 코드만, 다른 가이드에 일부

- …/Vehicle/vehicle-error-code: `ERROR_DRIVING_RESTRICTED` 「주행 중 기능 제한」.
- /docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy: 「DO 미선언 시 Parking 사용 가능 / **Drive 사용 불가**」.
- /docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide: 「CarUxRestrictionsManager.registerListener → onUxRestrictionsChanged 로 ActiveRestrictions 적용」.
- DO 선언 방법 · 앱 강제 종료 여부 · Foreground Service 동작 — NOT STATED.
- 외부 서버 · 로그인 정책 — NOT STATED.

## 8. 계획(SRC-01)과 다른 곳 요약

1. Gear 메서드 없음 → P/D 전환 트리거를 다른 수단으로 (CarUxRestrictions 주행 상태 · AAOS 표준 Car API 의 기어 속성 — 후자는 문서 밖 추정).
2. 조향각은 핸들 회전각이 아니라 앞바퀴 각 → 차선 변경 판정 기준을 다시 잡아야 함.
3. FusedLocation 속도 없음 → 「감속 부드러움」 추정 수단이 없음.
4. 앰비언트 set 퍼미션: CAR_LIGHTS (API Ref) ↔ CONTROL_CAR_LIGHTS (가이드).
5. 순정 내비 목적지 수신은 가능 (SRC-01 은 미확인으로 둠).
6. 벨트 퍼미션 CAR_SEATS 확인.
