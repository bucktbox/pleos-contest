# 세이프 카고 1차 제출 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 차량 신호로 "카고 호위"를 판정하고 정차 후 언박싱 · 조명 · 리워드로 보상하는 Pleos Connect 앱을 만들어, VC 스크립트 S1 을 3회 연속 크래시 없이 완주시킨다.

**Architecture:** 순수 Kotlin 엔진(`engine/`)이 `reduce(state, event) -> Step(state, effects)` 로 상태를 바꾸고, Android 쪽 `TripController` 가 SDK 콜백을 이벤트로 넣고 효과(위치 · 조명 · TTS · 저장)를 실행한다. Compose 화면은 `StateFlow<TripState>` 만 그린다. 엔진 · 컨트롤러 수명 = `Application`.

**Tech Stack:** Kotlin 2.0.21 · AGP 8.9.3 · Gradle 8.12 · Jetpack Compose (BOM 2025.10.01) · kotlinx.serialization 1.7.3 · JUnit 4 · Pleos SDK (Vehicle 2.3.2 · NaviHelper 3.0.5 · TextToSpeech 3.3.1 · FusedLocation 1.1.3) · `android.car` optional library

**Spec:** `docs/superpowers/specs/2026-11-02-safecargo-app-design.md` (2026-11-02 승인, 12절 A1~A6 기본값으로 확정). SDK 시그니처 · 함정은 `docs/reference/2026-11-02-poc-sdk-검증결과.md` 와 `poc/app/src/main/java/com/safecargo/poc/MainActivity.kt` 를 본다 (코드는 복사하지 않고 시그니처 · 호출 순서만 참고).

## Global Constraints

- 저장소 루트 `~/Desktop/Source/pleos-contest` 에 새 Gradle 프로젝트, 모듈 `:app` 하나. `poc/` 는 건드리지 않는다.
- 패키지 `com.safecargo`, applicationId `com.safecargo.app`. minSdk 28 · targetSdk 34 · compileSdk 36 · JVM 17 · `useLibrary("android.car")`.
- Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` (POC `settings.gradle.kts` 그대로).
- 권한은 정확히 9개: `pleos.car.permission.CAR_DRIVE` · `CAR_LIGHTS` · `CONTROL_CAR_LIGHTS` · `CAR_SEATS` · `NAVI_ROUTE` · `NAVI_ROUTE_SEARCH` · `TTS_SERVICE` · `FUSED_LOCATION`(런타임 요청) · `android.car.permission.CAR_DRIVING_STATE`. `CAR_SPEED` 선언 금지.
- `engine/` 은 Android import 0개 (`android.*`, `ai.pleos.*` 금지). 시간은 이벤트의 `atMs` 로만 받는다.
- 주행 중(phase `ESCORTING`) 화면: 터치 대상 0개, 문장 0개 — 숫자 · 아이콘만. 애니메이션 없음 (`drivingScreen="static"`). 소리 없음 (`drivingAudio=false`).
- 벌점 기본 0, 가점만. 화면 · 문구에 "무결점" · "HP" 를 쓰지 않는다. 스트릭 이름 "무사 배송".
- 금액 · 제휴명 · 스폰서명 바로 오른쪽 같은 줄에 「예시」 배지 (SD-08). 리워드 화면에 "시연용 가상 보상 · 실제 사용 불가". 사람 실명 금지.
- 등급은 색 + 이름(레어 · 에픽 · 레전더리) + 상자 모양 3중 표기 (SD-07). 상태 칩은 아이콘 + 글자 「정차 · 주행 · 일시 정차」 (「P단 · D단」 금지).
- 좌표 · 원시 신호는 저장 · 로그 금지. 로그 태그 `SAFECARGO`.
- 테마 다크 하나: 배경 `#090B0F`, accent `#4C8DFF`, 글꼴 Pretendard, 터치 최소 76dp.
- 창 등급: 폭 ≥ 960dp → L(가로, 좌측 레일), 아니면 P(세로, 상단 세그먼트). 높이 < 900 → S, 900~1199 → R, ≥ 1200 → T.
- 기준 창 12개 (dp): 1700×1184 · 840×1184 · 1360×948 · 672×948 · 1274×855 · 628×855 · 1640×810 · 800×810 · 1640×1400 · 800×1400 · 1256×800 · 648×800.
- 에뮬레이터는 항상 `adb -s emulator-5554`. 연결된 실폰 `R3CR30AT6XH` 에는 아무것도 하지 않는다.
- 커밋은 작업 파일만 개별 `git add` (`git add -A` 금지). 메시지 끝 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. push 는 Task 7 · 11 · 12 끝에서만.

## Review Focus

1. **P 직후 곧바로 D (위치 응답이 늦게 옴)** — 다시 호위 중인데 늦게 온 위치 결과로 개봉되면 안 된다 → Task 3 `late_location_after_redrive_is_ignored`.
2. **조향 값 재전송(약 3초마다 같은 값)** — 한 번의 회전이 두 번 판정되거나 급조향 계산이 흐트러지면 안 된다 → Task 2 `same_turn_judged_once_even_with_resend`.
3. **UX 제한 해제가 P 보다 약 0.4초 늦게 옴** — 도착이 맞으면 해제되는 순간 열려야 하고, 그 전엔 열리면 안 된다 → Task 3 `inside_radius_opens_only_after_ux_clears`.
4. **개봉 중 프로세스 재시작** — 리워드가 두 번 쌓이면 안 된다 → Task 3 `restore_opening_keeps_result_id` + Task 4 `append_same_result_twice_keeps_one`.
5. **조명이 바뀐 채 남음** — 개봉 중 재출발 · 앱이 가려짐 모두 원복 → Task 3 `drive_during_opening_restores_and_loads_daily` + Task 6/7 수동 확인(`onStop`).

---

## File Structure

```
settings.gradle.kts · build.gradle.kts · gradle.properties · gradlew · gradle/wrapper/*   (POC 에서 복사, rootProject.name="safecargo")
.gitignore                                    빌드 산출물 · local.properties · *.jks
scripts/s1-demo.sh                            VC + geo fix 로 S1 재현 (Task 12)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/config/judge.json         JudgeConfig 기본값 (Task 4)
app/src/main/assets/mock/catalog.json         스폰서 · 카고 · 리워드 Mock (Task 4)
app/src/main/res/font/pretendard_*.otf        Task 8
app/src/main/java/com/safecargo/
  engine/Signals.kt          Gear · TurnSignal · Seat · SeatSnapshot · VehicleSignal
  engine/JudgeConfig.kt      설정값 data class (기본값 = 설계 2.2)
  engine/JudgeEngine.kt      깜빡이 · 조향 · 버프 · 등급 판정 (순수 함수)
  engine/TripModels.kt       Cargo · Destination · Phase · TripState · TripResult · TripEvent · TripEffect · GeoFix
  engine/TripStateMachine.kt reduce()
  engine/Geo.kt              distanceM()
  data/ConfigLoader.kt       judge.json → JudgeConfig
  data/MockCatalog.kt        catalog.json → MockCatalog
  data/TripStore.kt          active_trip.json · trips.json
  data/Streak.kt             연속 일수
  sdk/VehicleSource.kt       Vehicle SDK → VehicleSignal, 지원 여부
  sdk/UxGate.kt              CarUxRestrictions → UxChanged
  sdk/LocationOnce.kt        FusedLocation 1회 → GeoFix?
  sdk/AmbientLight.kt        5영역 백업 · 색 · 원복
  sdk/TtsPlayer.kt           ON_DEVICE TTS
  sdk/NaviSource.kt          requestRoute · 목적지 이름
  app/SafeCargoApp.kt        Application — 조립
  app/TripController.kt      이벤트 디스패치 · 효과 실행 · 로그
  app/MainActivity.kt        Compose 진입 · 권한 · onStop 원복
  ui/theme/Tokens.kt · Theme.kt
  ui/shell/WindowClass.kt · AppShell.kt
  ui/CargoApp.kt             상태 → 화면 선택
  ui/screens/LobbyScreen.kt · DestinationScreen.kt · EscortScreen.kt · UnboxingScreen.kt · RewardScreen.kt
  ui/preview/WindowPreviews.kt
app/src/test/java/com/safecargo/
  engine/JudgeEngineTest.kt · TripStateMachineTest.kt · GeoTest.kt
  data/ConfigLoaderTest.kt · MockCatalogTest.kt · TripStoreTest.kt · StreakTest.kt
  ui/shell/WindowClassTest.kt
```

---

### Task 1: Gradle 프로젝트 골격 + 빈 Compose 화면

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (마지막 3개는 `poc/` 에서 복사), `.gitignore`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/safecargo/app/MainActivity.kt`

**Interfaces:**
- Produces: `./gradlew :app:assembleDebug` · `:app:testDebugUnitTest` 가 도는 프로젝트. 이후 모든 Task 가 여기에 파일을 더한다.

- [ ] **Step 1: 루트 설정 작성** — `settings.gradle.kts` 는 POC 와 같되 `rootProject.name = "safecargo"`. 루트 `build.gradle.kts` 플러그인: `com.android.application` 8.9.3, `org.jetbrains.kotlin.android` 2.0.21, `org.jetbrains.kotlin.plugin.compose` 2.0.21, `org.jetbrains.kotlin.plugin.serialization` 2.0.21 (모두 `apply false`). `.gitignore`: `.gradle/ build/ local.properties .idea/ *.iml captures/ .kotlin/ *.jks keystore.properties`.

- [ ] **Step 2: `app/build.gradle.kts`** — Global Constraints 의 sdk 값 · `useLibrary("android.car")` · `buildFeatures { compose = true; buildConfig = true }` · `testOptions.unitTests.isReturnDefaultValues = true`. release 는 `minify false`, `signingConfig = signingConfigs.getByName("debug")` (1차는 설치 가능한 APK 가 목표 — 주석으로 이유 기록). 의존성:
  - Pleos 4종 (POC 와 같은 버전)
  - `platform("androidx.compose:compose-bom:2025.10.01")`, `ui`, `ui-tooling-preview`, `foundation`, `material3`, debug `ui-tooling`
  - `androidx.activity:activity-compose:1.11.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.9.0`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`
  - test: `junit:junit:4.13.2`, `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2`
  > compileSdk 36 이 AGP 8.9.3 에서 경고 이상으로 막히면 compileSdk 35 + BOM `2025.05.01` + activity-compose `1.10.1` 로 내리고 이 줄을 계획서에 고쳐 적는다.

- [ ] **Step 3: Manifest** — 권한 9개(Global Constraints 그대로), `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>`, `application android:name=".app.SafeCargoApp"`(Task 7 전까지는 속성 생략), `MainActivity` exported LAUNCHER, `android:resizeableActivity="true"`, `<meta-data android:name="distractionOptimized" android:value="true"/>` (주행 중 SC-03 을 보여주려면 필요 — 대신 주행 화면은 조작 0개).

- [ ] **Step 4: `MainActivity : ComponentActivity`** — `setContent { Text("세이프 카고") }` 만.

- [ ] **Step 5: 빌드 · 설치 확인**
  Run: `./gradlew :app:assembleDebug && adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk && adb -s emulator-5554 shell am start -n com.safecargo.app/com.safecargo.app.MainActivity`
  Expected: BUILD SUCCESSFUL, `Success`, 에뮬레이터에 글자 표시.

- [ ] **Step 6: Commit**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradlew gradle/wrapper .gitignore app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/safecargo/app/MainActivity.kt
git commit -m "chore: 세이프 카고 앱 Gradle 골격"
```

---

### Task 2: 신호 모델 · JudgeConfig · JudgeEngine

**Files:**
- Create: `app/src/main/java/com/safecargo/engine/Signals.kt`, `JudgeConfig.kt`, `JudgeEngine.kt`
- Test: `app/src/test/java/com/safecargo/engine/JudgeEngineTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class TurnSignal { LEFT, RIGHT, OFF }
enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }
@Serializable data class SeatSnapshot(val occupied: Map<Seat, Boolean?> = emptyMap(), val belted: Map<Seat, Boolean?> = emptyMap())
sealed interface VehicleSignal { val atMs: Long }   // 하위: GearChanged(gear) · TurnChanged(signal) · SteerChanged(deg: Float /*보정 후: 음수=왼쪽*/) · SeatsChanged(seats) · UxChanged(restricted: Boolean) · Disconnected
@Serializable data class JudgeConfig(...)            // 아래 필드 · 기본값
enum class Grade { RARE, EPIC, LEGENDARY }
enum class MomentKind { SIGNAL_FIRST, PERFECT_SIGNAL, NO_SIGNAL_TURN, SMOOTH_TURN, HARSH_TURN }
@Serializable data class JudgeMoment(val atMs: Long, val kind: MomentKind)
@Serializable data class JudgeState(keys: Double = 0.0, signalFirst: Int = 0, perfect: Int = 0, noSignalTurns: Int = 0, smoothTurns: Int = 0, harshTurns: Int = 0, signalBonusCount: Int = 0, moments: List<JudgeMoment> = emptyList(), /* 추적용 */ signalDir: TurnSignal = OFF, signalOnAt: Long? = null, signalOffAt: Long? = null, lastDeg: Float? = null, lastAt: Long? = null, inTurn: Boolean = false, judged: Boolean = false, peakRate: Float = 0f)
data class JudgeOutcome(val baseKeys: Int, val finalKeys: Int, val familyBuff: Boolean, val beltVerified: Boolean, val grade: Grade)
object JudgeEngine {
  fun onTurn(s: JudgeState, signal: TurnSignal, atMs: Long, c: JudgeConfig): JudgeState
  fun onSteer(s: JudgeState, deg: Float, atMs: Long, c: JudgeConfig): JudgeState
  fun finalize(s: JudgeState, seats: SeatSnapshot, c: JudgeConfig): JudgeOutcome
  fun gradeOf(keys: Int, c: JudgeConfig): Grade
}
```
- `JudgeConfig` 필드 · 기본값: `drivingAudio=false`, `drivingScreen="static"`, `penaltyNoSignalTurn=0.0`, `penaltyHarshSteer=0.0`, `signalBonusKey=30.0`, `perfectSignalSec=1.5`, `perfectMultiplier=1.5`, `turnAngleDeg=90f`, `harshSteerDegPerSec=300f`, `smoothTurnKey=5.0`, `maxSignalBonusPerTrip=5`, `signalGraceSec=2.0`, `familyBuffMinPassengers=1`, `familyBuffMultiplier=1.5`, `beltSignalTrusted=false`, `gradeThresholds=listOf(60, 120)`, `gradeLightColors=mapOf(RARE to 0x00E5FF, EPIC to 0xAA00FF, LEGENDARY to 0xFFC400)`, `ambientFlashMs=3000L`, `rankingEnabled=false`, `streakDays=5`, `streakName="무사 배송"`, `arrivalRadiusM=300.0`, `locationMaxAgeSec=60`, `steerSign=-1f`, `momentsMax=12`.

판정 알고리즘 (설계 5절을 이 순서로):
1. `onTurn`: LEFT/RIGHT → `signalDir`, `signalOnAt=atMs`, `signalOffAt=null`. OFF → `signalOffAt=atMs` (`signalDir` 유지 — 유예용).
2. `onSteer`: `deg == lastDeg` 이면 **상태 그대로 반환** (재전송 무시, `lastAt` 도 안 바꿈). 변화율 `rate = |deg-lastDeg| / ((atMs-lastAt)/1000)` (이전 샘플 없으면 0, dt ≤ 0 이면 0).
3. `!inTurn` 이고 `|deg| ≥ turnAngleDeg/3` → `inTurn=true, judged=false, peakRate=rate`. `inTurn` 이면 `peakRate=max(peakRate, rate)`.
4. `inTurn && !judged && |deg| ≥ turnAngleDeg` → 판정 1회: 방향 = deg<0 ? LEFT : RIGHT. 깜빡이 인정 = `signalDir == 방향` 이고 (`signalOffAt == null` 또는 `atMs - signalOffAt ≤ signalGraceSec*1000`). 인정이면 `signalFirst++`, 퍼펙트 = `atMs - signalOnAt ≥ perfectSignalSec*1000`(→ `perfect++`), `signalBonusCount < max` 일 때만 `keys += signalBonusKey × (퍼펙트 ? perfectMultiplier : 1)` 이고 `signalBonusCount++`. 아니면 `noSignalTurns++`, `keys -= penaltyNoSignalTurn`. `judged=true`. moment 추가.
5. `inTurn && |deg| < turnAngleDeg/3` (이 샘플의 rate 까지 peak 에 반영한 뒤) → `judged` 였으면 `peakRate ≤ harshSteerDegPerSec` ? (`smoothTurns++`, `keys += smoothTurnKey`) : (`harshTurns++`, `keys -= penaltyHarshSteer`). `inTurn=false`.
6. `keys` 는 0 아래로 내려가지 않는다. `moments` 는 최근 `momentsMax` 개만.
7. `finalize`: 동승자 = `occupied[s]==true` 인 DRIVER 외 좌석. `beltVerified = beltSignalTrusted && 동승자 전원 belted==true`. 버프 = 동승자 수 ≥ `familyBuffMinPassengers` && (`!beltSignalTrusted` || `beltVerified`). `baseKeys=floor(keys)`, `finalKeys=floor(keys × (버프 ? multiplier : 1))`, `grade=gradeOf(finalKeys)`.

- [ ] **Step 1: 실패 테스트 작성** — 헬퍼 `c = JudgeConfig()`, `fun JudgeState.turn(sig, t) = JudgeEngine.onTurn(this, sig, t, c)`, `fun JudgeState.steer(d, t) = JudgeEngine.onSteer(this, d, t, c)`.

```kotlin
@Test fun signal_then_same_direction_turn_gives_bonus() {
    val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000)
    assertEquals(30.0, s.keys, 0.001); assertEquals(1, s.signalFirst); assertEquals(0, s.perfect)
}
@Test fun perfect_signal_after_1_5s_gives_1_5x() {
    val s = JudgeState().turn(LEFT, 0).steer(-90f, 1500)
    assertEquals(45.0, s.keys, 0.001); assertEquals(1, s.perfect)
}
@Test fun opposite_direction_counts_as_no_signal() {
    val s = JudgeState().turn(LEFT, 0).steer(120f, 1000)
    assertEquals(0.0, s.keys, 0.001); assertEquals(1, s.noSignalTurns); assertEquals(0, s.signalFirst)
}
@Test fun same_turn_judged_once_even_with_resend() {
    val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000).steer(-180f, 2000).steer(-180f, 5000)
    assertEquals(1, s.signalFirst); assertEquals(30.0, s.keys, 0.001); assertEquals(2000L, s.lastAt)
}
@Test fun rearm_after_return_below_third_and_smooth_bonus() {
    val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000).steer(-180f, 2000).steer(-20f, 3000)
        .turn(LEFT, 4000).steer(-90f, 5000)
    assertEquals(2, s.signalFirst); assertEquals(1, s.smoothTurns); assertEquals(65.0, s.keys, 0.001)
}
@Test fun grace_after_signal_off_still_counts() {
    val s = JudgeState().turn(LEFT, 0).turn(OFF, 1000).steer(-90f, 2500)
    assertEquals(1, s.signalFirst); assertEquals(45.0, s.keys, 0.001)
}
@Test fun signal_off_beyond_grace_is_no_signal() {
    val s = JudgeState().turn(LEFT, 0).turn(OFF, 1000).steer(-90f, 3500)
    assertEquals(0, s.signalFirst); assertEquals(1, s.noSignalTurns)
}
@Test fun bonus_capped_at_max_per_trip() {
    val cc = c.copy(smoothTurnKey = 0.0)
    var s = JudgeState(); var t = 0L
    repeat(6) {
        s = JudgeEngine.onTurn(s, LEFT, t, cc); s = JudgeEngine.onSteer(s, -90f, t + 1000, cc); s = JudgeEngine.onSteer(s, 0f, t + 2000, cc); t += 3000
    }
    assertEquals(6, s.signalFirst); assertEquals(150.0, s.keys, 0.001); assertEquals(5, s.signalBonusCount)
}
@Test fun harsh_steer_recorded_without_penalty_by_default() {
    val s = JudgeState().steer(0f, 0).steer(400f, 500).steer(0f, 1000)
    assertEquals(1, s.harshTurns); assertEquals(1, s.noSignalTurns); assertEquals(0.0, s.keys, 0.001)
}
@Test fun keys_never_negative_with_penalties() {
    val cc = c.copy(penaltyNoSignalTurn = 50.0, penaltyHarshSteer = 50.0)
    var s = JudgeEngine.onSteer(JudgeState(), 0f, 0, cc); s = JudgeEngine.onSteer(s, 400f, 500, cc); s = JudgeEngine.onSteer(s, 0f, 1000, cc)
    assertEquals(0.0, s.keys, 0.001)
}
@Test fun family_buff_with_one_passenger_when_belt_untrusted() {
    val seats = SeatSnapshot(occupied = mapOf(DRIVER to true, REAR_LEFT to true), belted = mapOf(DRIVER to false, REAR_LEFT to false))
    val o = JudgeEngine.finalize(JudgeState(keys = 30.0), seats, c)
    assertTrue(o.familyBuff); assertFalse(o.beltVerified); assertEquals(30, o.baseKeys); assertEquals(45, o.finalKeys)
}
@Test fun belt_trusted_requires_buckled_passengers() {
    val seats = SeatSnapshot(occupied = mapOf(DRIVER to true, REAR_LEFT to true), belted = mapOf(REAR_LEFT to false))
    assertFalse(JudgeEngine.finalize(JudgeState(keys = 30.0), seats, c.copy(beltSignalTrusted = true)).familyBuff)
}
@Test fun driver_alone_has_no_buff() {
    assertFalse(JudgeEngine.finalize(JudgeState(keys = 30.0), SeatSnapshot(occupied = mapOf(DRIVER to true)), c).familyBuff)
}
@Test fun grade_thresholds() {
    assertEquals(RARE, JudgeEngine.gradeOf(59, c)); assertEquals(EPIC, JudgeEngine.gradeOf(60, c)); assertEquals(LEGENDARY, JudgeEngine.gradeOf(120, c))
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :app:testDebugUnitTest --tests "com.safecargo.engine.JudgeEngineTest"` · Expected: 컴파일 실패(미정의 심볼).

- [ ] **Step 3: `Signals.kt` · `JudgeConfig.kt` · `JudgeEngine.kt` 구현** — 위 알고리즘 그대로. 모든 상태 변경은 `copy()`.

- [ ] **Step 4: 통과 확인** — 같은 명령 · Expected: 14 tests PASS. `grep -rn "import android\|import ai.pleos" app/src/main/java/com/safecargo/engine` 결과 0줄.

- [ ] **Step 5: Commit** — `git add app/src/main/java/com/safecargo/engine app/src/test/java/com/safecargo/engine/JudgeEngineTest.kt` · `feat(engine): 깜빡이 · 조향 · 패밀리 버프 판정`

---

### Task 3: TripStateMachine

**Files:**
- Create: `app/src/main/java/com/safecargo/engine/TripModels.kt`, `TripStateMachine.kt`, `Geo.kt`
- Test: `app/src/test/java/com/safecargo/engine/TripStateMachineTest.kt`, `GeoTest.kt`

**Interfaces:**
- Consumes: Task 2 전부.
- Produces:
```kotlin
enum class CargoKind { SPONSOR, DAILY }
@Serializable data class Destination(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val rewardId: String)
@Serializable data class Cargo(val id: String, val name: String, val kind: CargoKind, val destination: Destination? = null)
enum class Phase { IDLE, LOADED, ESCORTING, PAUSED, OPENING }
@Serializable data class TripSummary(val signalFirst: Int, val noSignalTurns: Int, val perfect: Int, val smoothTurns: Int, val moments: List<JudgeMoment>)
@Serializable data class TripResult(val id: String, val cargoName: String, val destinationName: String?, val grade: Grade, val keys: Int, val familyBuff: Boolean, val beltVerified: Boolean, val arrivalVerified: Boolean, val rewardId: String, val epochDay: Long, val summary: TripSummary)
@Serializable data class TripState(
    val phase: Phase = Phase.IDLE, val cargo: Cargo? = null, val judge: JudgeState = JudgeState(), val seats: SeatSnapshot = SeatSnapshot(),
    val checkingArrival: Boolean = false, val openAfterLocation: Boolean = false, val arrivalVerified: Boolean = false, val autoOpenPending: Boolean = false,
    val result: TripResult? = null, val revealed: Boolean = false, val lastResult: TripResult? = null,
    @Transient val gear: Gear = Gear.UNKNOWN, @Transient val uxRestricted: Boolean = true, @Transient val connected: Boolean = false,
) { val canArrive: Boolean get() = phase == Phase.PAUSED && gear == Gear.PARK && !uxRestricted && !checkingArrival }
data class GeoFix(val lat: Double, val lng: Double, val ageMs: Long)
sealed interface TripEvent  // Signal(VehicleSignal) · SelectDestination(Destination) · LocationResolved(GeoFix?) · ArriveConfirmed · RevealFinished · CloseResult · Restore(TripState) · Reset
sealed interface TripEffect // RequestLocation · RequestRoute(Destination) · FlashLights(Grade) · RestoreLights · AnnounceArrival · StopSpeech · PersistActive(TripState) · ClearActive · SaveResult(TripResult)
data class Step(val state: TripState, val effects: List<TripEffect> = emptyList())
class TripStateMachine(config: JudgeConfig, dailyCargo: Cargo, newId: () -> String, todayEpochDay: () -> Long) { fun reduce(s: TripState, e: TripEvent): Step }
fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double   // haversine, R=6_371_000
```
- 상수: 리워드 id `"general"`(스폰서 · 인증 없음) · `"daily"`(데일리). 스폰서 카고는 엔진이 목적지로 만든다: id = `"cargo-" + destination.id`, 이름 = `destination.name + " 카고"` (Mock 에 카고 목록을 따로 두지 않는다).

전이 규칙 (설계 4절. 표에 없는 조합은 상태 그대로 · 효과 없음):

| 현재 | 이벤트 | 다음 · 효과 |
| :--- | :--- | :--- |
| 모든 phase | `GearChanged` · `UxChanged` · `SeatsChanged` · `Disconnected` | 먼저 `gear` · `uxRestricted` · `seats` · `connected` 갱신 (`Disconnected` → gear UNKNOWN, ux true, connected false; 그 외 신호 수신 시 connected true) |
| IDLE | SelectDestination(d) | LOADED, 스폰서 카고 · `RequestRoute(d)` · `PersistActive` |
| LOADED | SelectDestination(d) | 카고 교체 · 같은 효과 |
| IDLE | Gear DRIVE/REVERSE | ESCORTING, `dailyCargo`, `judge=JudgeState()` · `PersistActive` |
| LOADED | Gear DRIVE/REVERSE | ESCORTING · `PersistActive` |
| ESCORTING | TurnChanged / SteerChanged | `judge` 갱신만 (다른 phase 에선 무시) |
| ESCORTING | Gear PARK | PAUSED. 스폰서면 `checkingArrival=true` · `RequestLocation`. 데일리면 바로 PAUSED. 둘 다 `PersistActive` |
| PAUSED | Gear DRIVE/REVERSE | ESCORTING, `checkingArrival/openAfterLocation/autoOpenPending=false` · `PersistActive` (judge 유지) |
| PAUSED | LocationResolved(fix) (`checkingArrival` 일 때만) | 인증 = fix≠null && `ageMs ≤ locationMaxAgeSec*1000` && `distanceM ≤ arrivalRadiusM`. `checkingArrival=false`, `arrivalVerified=인증`. `openAfterLocation` 이면 → 개봉. 아니면 인증 시 `autoOpenPending=true` |
| PAUSED | (위 처리 뒤 매 이벤트) `autoOpenPending && gear==PARK && !uxRestricted` | → 개봉 |
| PAUSED | ArriveConfirmed (`canArrive` 일 때만) | 데일리 → 개봉. 스폰서 → `checkingArrival=true, openAfterLocation=true` · `RequestLocation` |
| (개봉) | — | OPENING. `JudgeEngine.finalize(judge, seats)` → `TripResult(id=newId(), rewardId = 데일리 ? "daily" : 인증 ? destination.rewardId : "general", epochDay=todayEpochDay(), ...)`. `result=…, revealed=false`. 효과 순서: `SaveResult(r)`, `PersistActive`, `FlashLights(grade)`, `AnnounceArrival` |
| OPENING | RevealFinished | `revealed=true` · `PersistActive` |
| OPENING | CloseResult | IDLE, `lastResult=result`, 나머지 초기화 · `RestoreLights`, `ClearActive` |
| OPENING | Gear DRIVE/REVERSE | (결과는 이미 저장됨) ESCORTING + 새 `dailyCargo` + 새 judge, `lastResult=result`, `result=null` · `RestoreLights`, `StopSpeech`, `PersistActive` |
| 모든 phase | Gear NEUTRAL/UNKNOWN | 갱신만, 전이 없음 |
| 모든 phase | Restore(saved) | `saved` 를 쓰되 transient 기본값. `saved.phase==OPENING && result!=null` 이면 효과 `SaveResult(result)` (저장소가 중복을 막는다) |
| 모든 phase | Reset | `TripState()` · `RestoreLights`, `ClearActive` |

- [ ] **Step 1: 실패 테스트 작성** — 고정값: `dest = Destination("d1","파주 베이커리 카페 예시","주소",37.0,127.0,"coupon-cafe")`, `daily = Cargo("daily","데일리 카고",DAILY)`, ids `"r1","r2",…` 순서 생성, today `20000L`. 헬퍼 `fun TripState.on(vararg e: TripEvent): Step` (앞 Step 상태를 이어 마지막 Step 반환), `gear(g) = TripEvent.Signal(VehicleSignal.GearChanged(g, t))`, `ux(b)` 동일. `near = GeoFix(37.0005, 127.0, 800)` (약 56m), `far = GeoFix(37.1, 127.0, 800)`.
  테스트 (이름 → 단언):
  - `idle_drive_autoloads_daily_and_escorts` → phase ESCORTING, cargo.id "daily", effects 에 PersistActive
  - `select_destination_loads_sponsor_and_requests_route` → LOADED, cargo.kind SPONSOR, effects == [RequestRoute(dest), PersistActive(_)] (순서 포함)
  - `park_with_sponsor_requests_location` → PAUSED, checkingArrival, effects 에 RequestLocation
  - `inside_radius_opens_only_after_ux_clears` → (LOADED→DRIVE→PARK→LocationResolved(near)) 뒤 phase PAUSED · autoOpenPending; 이어서 ux(false) → OPENING, result.arrivalVerified true, rewardId "coupon-cafe", effects 첫 원소 SaveResult, FlashLights 포함
  - `outside_radius_stays_paused_and_can_arrive` → PAUSED, !arrivalVerified, ux(false) 뒤 canArrive true
  - `arrive_on_sponsor_relocates_then_opens_unverified` → ArriveConfirmed → RequestLocation; LocationResolved(null) → OPENING, rewardId "general", arrivalVerified false
  - `stale_fix_counts_as_failure` → GeoFix(37.0,127.0, 61_000) → !arrivalVerified
  - `daily_park_needs_arrive_tap` → DRIVE(daily)→PARK 효과에 RequestLocation 없음; ux(false) 뒤 ArriveConfirmed → OPENING rewardId "daily"
  - `arrive_ignored_while_ux_restricted` → PAUSED 에서 ArriveConfirmed → 상태 동일, effects 빔
  - `drive_during_opening_restores_and_loads_daily` → effects 에 RestoreLights · StopSpeech, SaveResult 없음; phase ESCORTING, cargo "daily", lastResult.id "r1", judge.keys 0
  - `neutral_and_unknown_do_not_transition` → ESCORTING+NEUTRAL → ESCORTING; PAUSED+UNKNOWN → PAUSED, canArrive false
  - `late_location_after_redrive_is_ignored` → PARK 후 DRIVE → LocationResolved(near) → ESCORTING, effects 빔
  - `steering_ignored_outside_escorting` → LOADED 에서 TurnChanged+SteerChanged → judge == JudgeState()
  - `buff_uses_latest_seats` → 호위 중 SeatsChanged(운전자+뒷좌석) 뒤 개봉 → result.familyBuff true
  - `restore_opening_keeps_result_id` → Restore(OPENING, result r1) → state.result.id "r1", effects == [SaveResult(r1)], ids 생성기 미호출 (다음 id 가 여전히 "r1")
  - `restore_resets_transient_fields` → gear UNKNOWN, uxRestricted true
  - `close_result_returns_idle_with_last_result` → IDLE, lastResult.id "r1", effects 에 RestoreLights · ClearActive
  - `GeoTest.distance_seoul_cityhall_to_gwanghwamun` → `distanceM(37.5665,126.9780,37.5759,126.9769)` 1040±20
- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests "com.safecargo.engine.*"` · 컴파일 실패.
- [ ] **Step 3: 구현** — `reduce` 는 ① 신호 필드 갱신 ② phase 별 `when` ③ 마지막에 autoOpen 검사 순서. 개봉은 private `open(s): Step` 하나로.
- [ ] **Step 4: 통과 확인** — 같은 명령 · Task 2 포함 전부 PASS.
- [ ] **Step 5: Commit** — `feat(engine): 운행 상태 머신 (적재 · 호위 · 일시 정차 · 개봉)`

---

### Task 4: 설정 · Mock · 저장소 · 스트릭

**Files:**
- Create: `app/src/main/java/com/safecargo/data/ConfigLoader.kt`, `MockCatalog.kt`, `TripStore.kt`, `Streak.kt`, `app/src/main/assets/config/judge.json`, `app/src/main/assets/mock/catalog.json`
- Test: `app/src/test/java/com/safecargo/data/ConfigLoaderTest.kt`, `MockCatalogTest.kt`, `TripStoreTest.kt`, `StreakTest.kt` (테스트는 `src/main/assets` 파일을 `File("src/main/assets/...")` 로 읽는다 — 작업 디렉터리 = `app/`)

**Interfaces:**
- Consumes: `JudgeConfig`, `Destination`, `Cargo`, `TripState`, `TripResult` (Task 2·3)
- Produces:
```kotlin
val SafeCargoJson: Json   // ignoreUnknownKeys=true, encodeDefaults=true
object ConfigLoader { fun parse(json: String): JudgeConfig }
@Serializable data class Reward(val id: String, val title: String, val type: RewardType /*COUPON, POINT, BADGE*/, val amountLabel: String, val partnerLabel: String?)
@Serializable data class MockCatalog(val destinations: List<Destination>, val daily: Cargo, val rewards: List<Reward>) { fun reward(id: String): Reward? }
object MockCatalogLoader { fun parse(json: String): MockCatalog }
class TripStore(dir: File) { fun saveActive(s: TripState); fun loadActive(): TripState?; fun clearActive(); fun appendResult(r: TripResult): Boolean; fun results(): List<TripResult>; fun clearAll() }
object Streak { fun days(results: List<TripResult>, todayEpochDay: Long): Int }
```
- `TripStore`: 파일 `active_trip.json` · `trips.json`. 쓰기는 `*.tmp` 에 쓰고 `renameTo` (원자적). 깨진 JSON 은 null / 빈 목록 (예외 밖으로 안 냄). `appendResult` 는 같은 id 가 있으면 `false`.
- `Streak.days`: 결과의 `epochDay` 집합에서 오늘(없으면 어제)부터 거꾸로 연속된 날 수. 오늘 · 어제 둘 다 없으면 0.
- `judge.json`: `JudgeConfig()` 기본값 전부를 명시적으로 적는다 (기획이 고칠 파일 — 키 이름은 data class 와 동일).
- `catalog.json`: 목적지 3곳 — `muni`「시청 광장 예시」(37.5665, 126.9780) · `rest`「휴게소 예시」(37.2636, 127.0286 근처 가상) · `cafe`「베이커리 카페 예시」(37.7130, 126.7600 근처 가상), 각 `rewardId` `coupon-muni`/`coupon-rest`/`coupon-cafe`. `daily` = `Cargo("daily","오늘의 데일리 카고","DAILY")`. 리워드 5개: 위 쿠폰 3개 + `general`(포인트 「100P」) + `daily`(포인트 「50P」). 모든 `title`/`partnerLabel` 은 가상 이름 (실존 상호 금지).

- [ ] **Step 1: 실패 테스트** —
  - `ConfigLoaderTest.asset_matches_defaults` → `parse(File("src/main/assets/config/judge.json").readText()) == JudgeConfig()`
  - `ConfigLoaderTest.unknown_and_missing_keys_tolerated` → `parse("""{"signalBonusKey":40,"foo":1}""")` 의 `signalBonusKey==40.0`, `turnAngleDeg==90f`
  - `MockCatalogTest.asset_has_three_sponsors_and_rewards_resolve` → 목적지 3개, 각 `reward(d.rewardId) != null`, `reward("general")` · `reward("daily")` != null
  - `TripStoreTest.save_load_active_roundtrip` (TemporaryFolder) → 저장한 `TripState(phase=PAUSED, …)` 와 로드 값의 phase · cargo · judge 동일, gear UNKNOWN
  - `TripStoreTest.append_same_result_twice_keeps_one` → 두 번째 `appendResult` false, `results().size==1`
  - `TripStoreTest.corrupt_files_read_as_empty` → 파일에 `"{"` 쓰고 `loadActive()==null`, `results()` 빔
  - `StreakTest` → 결과 없음 0 · 오늘만 1 · 어제+그제 2 · 오늘+어제+3일 전 2 · 같은 날 3건 1
- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests "com.safecargo.data.*"`
- [ ] **Step 3: 구현** (위 Interfaces 대로)
- [ ] **Step 4: 통과 확인** — `./gradlew :app:testDebugUnitTest` 전부 PASS
- [ ] **Step 5: Commit** — `feat(data): 판정 설정 · Mock 카탈로그 · 운행 저장소`

---

### Task 5: 차량 신호 · UX 게이트 래퍼

**Files:**
- Create: `app/src/main/java/com/safecargo/sdk/VehicleSource.kt`, `UxGate.kt`

**Interfaces:**
- Consumes: `VehicleSignal`, `Gear`, `TurnSignal`, `Seat`, `SeatSnapshot`, `JudgeConfig.steerSign`
- Produces:
```kotlin
data class SupportStatus(val gear: Boolean? = null, val turn: Boolean? = null, val steer: Boolean? = null, val seats: Boolean? = null, val belts: Boolean? = null, val lights: Boolean? = null, val location: Boolean? = null)  // null = 확인 중
class VehicleSource(context: Context, steerSign: Float, now: () -> Long = SystemClock::elapsedRealtime) {
  val signals: SharedFlow<VehicleSignal>   // MutableSharedFlow(extraBufferCapacity = 64), binder 스레드에서 tryEmit
  val support: StateFlow<SupportStatus>
  val vehicle: Vehicle?                    // AmbientLight 가 getLight() 로 쓴다
  fun start(); fun stop(); fun refreshSeats()
}
class UxGate(context: Context, now: () -> Long = SystemClock::elapsedRealtime) { val signals: SharedFlow<VehicleSignal.UxChanged>; fun start(); fun stop() }
```
- POC 에서 가져올 것: `Vehicle(context).initialize()` 후 `getGear().getCurrentGearSelectionState` 가 성공할 때까지 500ms 간격 최대 10회 → 성공 시 리스너 등록 + 초기값 1회 읽기. 10회 실패면 `Disconnected` 방출 + `support.gear=false`.
- 매핑: `VehicleGear.GEAR_PARK→PARK`, `GEAR_DRIVE` 와 `GEAR_1..9 → DRIVE`, `GEAR_REVERSE→REVERSE`, `GEAR_NEUTRAL→NEUTRAL`, null · 그 외 `UNKNOWN`. `TurnSignalValue` LEFT/RIGHT/그 외 OFF. 조향 `deg = raw * steerSign` (null 이면 버림). 좌석 `SeatValue.ROW_1_LEFT→DRIVER, ROW_1_RIGHT→PASSENGER, ROW_2_LEFT→REAR_LEFT, ROW_2_RIGHT→REAR_RIGHT`, 착석 · 벨트 콜백마다 누적 스냅샷 전체를 `SeatsChanged` 로.
- 각 리스너 `onFailed` 는 그 항목 `support=false` 로만 (루프 계속, 설계 8절). 등록 성공 · 첫 값 수신 시 `true`.
- `UxGate`: `Car.createCar` → `CarUxRestrictionsManager`, 초기값 + 리스너, `isRequiresDistractionOptimization` 를 `UxChanged(restricted)` 로. 실패 시 `restricted=true` 하나 방출 (잠금 유지).
- 수동 검증용으로 `MainActivity` 에 임시로 두 Flow 를 collect 해 `Log.i("SAFECARGO", …)` (Task 7 에서 컨트롤러로 대체 — 이 임시 코드는 그때 지운다).

- [ ] **Step 1: 구현** (테스트 대상 아님 — SDK 의존. 매핑 로직만 private 함수로 분리)
- [ ] **Step 2: 에뮬레이터 확인** — 설치 후 `adb -s emulator-5554 forward tcp:8088 tcp:8088`, `bash poc/scripts/vc-scenario.sh`, 동시에 `adb -s emulator-5554 logcat -s SAFECARGO`.
  Expected: `GearChanged(DRIVE)` · `TurnChanged(LEFT)` · VC 조향 -90 일 때 `SteerChanged(-90.0)` (보정 후 음수=왼쪽) · `SeatsChanged` · D 후 `UxChanged(true)` · P 후 `UxChanged(false)`.
- [ ] **Step 3: Commit** — `feat(sdk): 차량 신호 · UX 제한 래퍼`

---

### Task 6: 위치 · 조명 · TTS · 내비 래퍼

**Files:**
- Create: `app/src/main/java/com/safecargo/sdk/LocationOnce.kt`, `AmbientLight.kt`, `TtsPlayer.kt`, `NaviSource.kt`

**Interfaces:**
- Consumes: `GeoFix`, `Destination`, `VehicleSource.vehicle`
- Produces:
```kotlin
class LocationOnce(context: Context) { fun start(); val supported: Boolean?; fun request(onResult: (GeoFix?) -> Unit) }   // 콜백은 메인 스레드
class AmbientLight(vehicle: () -> Vehicle?, main: Handler) { fun flash(rgb: Int, durationMs: Long, onResult: (applied: Boolean) -> Unit); fun restore() }
class TtsPlayer(context: Context) { fun start(); fun speak(text: String); fun stop(); fun release() }
class NaviSource(context: Context) { fun start(); fun requestRoute(d: Destination); val destinationName: StateFlow<String?> }
```
- `LocationOnce.request`: 권한 미허용 · `isSupported()==false` · `onError` · null → `onResult(null)`. 값이면 `GeoFix(lat, lng, ageMs = System.currentTimeMillis() - timestampMillis)`. 좌표는 로그 금지.
- `AmbientLight.flash`: `checkAmbientLightBrightnessColorCapabilities` → 모든 영역 `get` 으로 백업(null 영역은 건드리지 않음) → `set(0xFF000000 or rgb)` → `durationMs` 뒤 `restore()`. `restore()` 는 백업이 있는 영역만 되돌리고 백업을 비운다 (여러 번 불러도 안전, 대기 중인 지연 원복 취소). 한 영역도 못 바꾸면 `onResult(false)`.
- `TtsPlayer`: `TextToSpeech(context, Mode.ON_DEVICE)`, `initialize` 성공 **뒤에** `addEventListener`. 초기화 전 `speak` 는 무시. 실패해도 예외 밖으로 안 냄 (EX-07).
- `NaviSource`: `NaviHelper.initialize` 성공 시 리스너 + `getDestinationInfo()`. `onDestinationInfo` → `destinationName`. `requestRoute` 는 `RouteInfo(lat, lng, null, null, name, "", address, "", RouteOption.RECOMMENDED, "", false)` (POC 와 같은 인자 순서). 실패는 로그만.

- [ ] **Step 1: 구현**
- [ ] **Step 2: 에뮬레이터 확인** — Task 5 의 임시 디버그 화면에 버튼 4개(조명 3초 · TTS · 위치 1회 · 경로 요청 시청)를 잠시 붙여 누른다. Expected: VC 조명이 3초 바뀌었다 원래 색으로 돌아옴 / 로그 `tts ok` / `location fix age=…ms`(좌표 없음) / 내비에 경로. 확인 후 버튼은 지운다.
- [ ] **Step 3: Commit** — `feat(sdk): 위치 1회 · 조명 · TTS · 내비 래퍼`

---

### Task 7: TripController · Application 조립 (11/04 마일스톤)

**Files:**
- Create: `app/src/main/java/com/safecargo/app/SafeCargoApp.kt`, `TripController.kt`
- Modify: `app/src/main/java/com/safecargo/app/MainActivity.kt` (임시 로그 · 버튼 제거), `AndroidManifest.xml` (`android:name`)

**Interfaces:**
- Consumes: Task 3~6 전부
- Produces:
```kotlin
class SafeCargoApp : Application() { lateinit var controller: TripController; lateinit var catalog: MockCatalog; lateinit var config: JudgeConfig }
class TripController(machine: TripStateMachine, store: TripStore, vehicle: VehicleSource, ux: UxGate, location: LocationOnce, light: AmbientLight, tts: TtsPlayer, navi: NaviSource, config: JudgeConfig, scope: CoroutineScope /* Main */) {
  val state: StateFlow<TripState>
  val support: StateFlow<SupportStatus>
  val borderLight: StateFlow<Grade?>      // 조명 실패 시 화면 테두리 대체 (FR-VH-012), ambientFlashMs 뒤 null
  val streak: StateFlow<Int>
  var appVisible: Boolean
  fun start()                              // store.loadActive() → Restore, 이후 신호 collect
  fun dispatch(e: TripEvent)               // 메인 스레드에서만
  fun onAppHidden()                        // light.restore()
  fun debugReset()                         // store.clearAll() + Reset (BuildConfig.DEBUG 에서만 호출)
}
```
- `dispatch`: `reduce` → 상태 갱신 → 효과 실행 → `Log.i("SAFECARGO", "<event> : <old phase> -> <new phase> keys=<n>")` (좌표 · 원시 조향값 제외).
- 효과 실행: `RequestLocation` → `location.request { dispatch(LocationResolved(it)) }` · `RequestRoute` → navi · `FlashLights(g)` → `light.flash(config.gradeLightColors[g], config.ambientFlashMs) { ok -> if (!ok) borderLight 를 g 로 }` · `RestoreLights` → `light.restore()` · `AnnounceArrival` → `tts.speak(appVisible ? "배송 완료! 상자를 열어볼까요?" : "카고가 도착했어요. 세이프 카고를 열어 개봉하세요")` · `StopSpeech` → `tts.stop()` · `PersistActive` → `store.saveActive` · `ClearActive` · `SaveResult` → `store.appendResult` 후 `streak` 갱신.
- 기어 PARK 신호를 받으면 `vehicle.refreshSeats()` 도 부른다 (콜백 누락 보완).
- `MainActivity`: `onStart` 에서 `appVisible=true`, `onStop` 에서 `appVisible=false` + `onAppHidden()`. `FUSED_LOCATION` 미허용이면 요청 (Task 9 의 권한 카드가 생기면 그쪽으로 옮김).

- [ ] **Step 1: 구현**
- [ ] **Step 2: S1 로그 확인** — `poc/scripts/vc-scenario.sh` 를 돌리기 전 디버그 인텐트로 목적지 선택: `MainActivity` 에 `intent.getStringExtra("dest")` 가 있으면 해당 Mock 목적지로 `SelectDestination` (디버그 빌드만). `adb -s emulator-5554 shell am start -n com.safecargo.app/.app.MainActivity --es dest cafe` → `adb -s emulator-5554 emu geo fix 126.7600 37.7130` → 스크립트 실행.
  Expected 로그 순서: `LOADED` → `ESCORTING` (keys 증가: 깜빡이 좌회전 30 이상) → `PAUSED` → `LocationResolved` → `OPENING` (rewardId coupon-cafe), 조명 3초 후 원복.
- [ ] **Step 3: 복구 확인 (EX-08)** — 호위 중 `adb -s emulator-5554 shell am force-stop com.safecargo.app` → 재실행 → 로그 `Restore : -> ESCORTING`. 개봉 중 force-stop → 재실행 → `trips.json` 항목 1개(`adb -s emulator-5554 shell run-as com.safecargo.app cat files/trips.json`).
- [ ] **Step 4: Commit + push** — `feat(app): 운행 컨트롤러 — SDK 신호와 엔진 연결`, `GIT_TERMINAL_PROMPT=0 git push`

---

### Task 8: 테마 · 창 등급 · 공통 셸 (CM-01)

**Files:**
- Create: `app/src/main/java/com/safecargo/ui/theme/Tokens.kt`, `Theme.kt`, `ui/shell/WindowClass.kt`, `ui/shell/AppShell.kt`, `ui/preview/WindowPreviews.kt`, `app/src/main/res/font/pretendard_regular.otf` · `pretendard_medium.otf` · `pretendard_bold.otf`, `app/src/main/assets/licenses/pretendard-OFL.txt`
- Test: `app/src/test/java/com/safecargo/ui/shell/WindowClassTest.kt`

**Interfaces:**
- Consumes: `TripState`, `SupportStatus`, `Phase`
- Produces:
```kotlin
enum class Orientation { L, P }; enum class HeightClass { S, R, T }
data class WindowClass(val orientation: Orientation, val height: HeightClass)
fun windowClassOf(widthDp: Int, heightDp: Int): WindowClass
object SC { val bg: Color; val surface: Color; val accent: Color; val text: Color; val textDim: Color; val rare: Color; val epic: Color; val legendary: Color; val touchMin: Dp /*76.dp*/; val gap: Dp }
@Composable fun SafeCargoTheme(content: @Composable () -> Unit)
@Composable fun AppShell(state: TripState, streak: Int, support: SupportStatus, wc: WindowClass, content: @Composable () -> Unit)
@Preview(name=...) annotation class WindowPreviews   // 12개 창 @Preview(widthDp, heightDp, showBackground = true, backgroundColor = 0xFF090B0F)
```
- 색 · 간격 값은 피그마 파일 `4Mq81UW9aSEY7IwbUDvJND` 「시스템 가이드」 변수에서 가져온다 (`get_variable_defs`; 사용 전 `/figma-design-to-code` 스킬 로드). 확정값: 배경 `#090B0F`, accent `#4C8DFF`, `size-touch-min` 76.
- Pretendard: GitHub `orioncactus/pretendard` 릴리스 v1.3.9 의 `Pretendard-Regular/Medium/Bold.otf` 와 OFL 라이선스. 받은 zip 은 scratchpad 의 빈 디렉터리에 풀고 필요한 파일만 복사.
- `AppShell`: 앱 바(앱 이름 · 상태 칩 아이콘+글자 「정차」(IDLE/LOADED/OPENING) 「주행」(ESCORTING) 「일시 정차」(PAUSED) · 버프 아이콘 `×1.5`(동승자 있을 때) · 「무사 배송 N일」, 0일이면 흐린 아이콘만) + 지원 배너(SupportStatus 에 false 가 있을 때만 한 줄, 예: 「이 차량은 벨트를 감지하지 못해요 — 버프는 착석 기준」) + 본문. L: 좌측 레일, P: 앱 바 아래 세그먼트. 레일 · 세그먼트 항목은 「로비」 하나 (`rankingEnabled` 가 true 면 「랭킹」 추가). ESCORTING 이면 레일 · 세그먼트를 숨긴다. 앱 바에 버튼 없음.
- T 높이는 본문 최대 높이 1184dp, 가운데 정렬 (RL-05).

- [ ] **Step 1: 실패 테스트** — `WindowClassTest.all_twelve_windows`: (1700,1184)=L·R, (840,1184)=P·R, (1360,948)=L·R, (672,948)=P·R, (1274,855)=L·S, (628,855)=P·S, (1640,810)=L·S, (800,810)=P·S, (1640,1400)=L·T, (800,1400)=P·T, (1256,800)=L·S, (648,800)=P·S. 경계: (960,900)=L·R, (959,899)=P·S, (1000,1200)=L·T.
- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests "*WindowClassTest*"`
- [ ] **Step 3: 구현** — `windowClassOf` + 테마 + 셸 + 프리뷰 (본문 자리에 빈 Box)
- [ ] **Step 4: 통과 확인** — 테스트 PASS, `./gradlew :app:assembleDebug` 성공
- [ ] **Step 5: Commit** — `feat(ui): 다크 테마 · 창 등급 · 공통 셸`

---

### Task 9: SC-01 로비 · SC-02 목적지 · 시작 안내

**Files:**
- Create: `app/src/main/java/com/safecargo/ui/screens/LobbyScreen.kt`, `DestinationScreen.kt`, `ui/CargoApp.kt`
- Modify: `app/MainActivity.kt` (`setContent { SafeCargoTheme { CargoApp(controller, catalog, config) } }`, 권한 요청을 콜백으로 넘김)

**Interfaces:**
- Consumes: `TripController.state/streak/support/dispatch`, `MockCatalog`, `AppShell`, `WindowClass`
- Produces:
```kotlin
@Composable fun CargoApp(controller: TripController, catalog: MockCatalog, config: JudgeConfig, onRequestLocation: () -> Unit, locationGranted: Boolean)
@Composable fun LobbyScreen(state: TripState, wc: WindowClass, locationGranted: Boolean, onPickDestination: () -> Unit, onRequestLocation: () -> Unit, onDebugReset: (() -> Unit)?)
@Composable fun DestinationScreen(destinations: List<Destination>, rewards: MockCatalog, wc: WindowClass, onSelect: (Destination) -> Unit, onBack: () -> Unit)
```
- `CargoApp` 화면 선택: ESCORTING · PAUSED → `EscortScreen`(Task 10), OPENING → `revealed ? RewardScreen : UnboxingScreen`(Task 11), 그 외 → 로비 (로비 안에서 `rememberSaveable` 로 목적지 화면 토글). Task 10·11 전에는 해당 분기에 화면 이름 텍스트만.
- 창 크기: `LocalConfiguration.current.screenWidthDp/screenHeightDp` → `windowClassOf`.
- 로비 내용 (화면정의서 SC-01): 오늘의 카고(이름 + 상자 그림) · 목적지 행(이름 + 「예시」 + [변경], 없으면 [목적지 고르기]) · 열쇠 3개 아이콘(깜빡이 · 부드럽게 · 패밀리) · 좌석 맵 4칸(착석 ●/○, 벨트 신뢰 시 ✓) + 버프 `×1.5` · 스트릭 「무사 배송 N일」 · 하단 안내 「출발하면 호위가 시작돼요」 · 위치 권한 카드(미허용일 때만, 문구 「도착했는지 정차할 때 한 번만 확인하고 바로 지워요. 저장 · 전송하지 않아요」, 버튼 → `onRequestLocation`) · 디버그 빌드만 [기록 초기화]. L: 주 영역 60% / 보조 40% 2단, P: 1단 + S 높이는 세로 스크롤.
- 로비 레이아웃 · 간격 · 글자 크기는 피그마 「02 SC-01 로비」 프레임을 `get_design_context` 로 받아 맞춘다 (기준 창 1700×1184 · 840×1184).
- 목적지 화면: 목록 순서 = `catalog.json` 순서 그대로 (거리순 정렬 금지), 각 행 이름 + 「예시」 + 리워드 요약 + 「예시」. 선택 → `dispatch(SelectDestination(d))` 후 로비로.

- [ ] **Step 1: 구현** + 각 화면 `@WindowPreviews` 프리뷰 (샘플 `TripState`)
- [ ] **Step 2: 확인** — `./gradlew :app:assembleDebug`, 설치 후 1700×1184 (기본) 스크린샷 `adb -s emulator-5554 exec-out screencap -p > <scratchpad>/sc01-L.png`, 내비와 나란히 띄운 세로 창(840×1184)도 1장. 피그마 프레임 스크린샷과 나란히 비교해 차이를 목록으로 남기고 고친다. 목적지 선택 → 로비에 이름 표시, 내비에 경로.
- [ ] **Step 3: Commit** — `feat(ui): 로비 · 목적지 선택 화면`

---

### Task 10: SC-03 호위 · SC-04 일시 정차

**Files:**
- Create: `app/src/main/java/com/safecargo/ui/screens/EscortScreen.kt`
- Modify: `ui/CargoApp.kt` (분기 연결)

**Interfaces:**
- Consumes: `TripState`, `JudgeState`, `WindowClass`
- Produces: `@Composable fun EscortScreen(state: TripState, wc: WindowClass, onArrive: () -> Unit)`
- ESCORTING: 카고 상자 그림 1개 + 열쇠 아이콘 3개 + 숫자 `floor(judge.keys)` + 착석 버프 아이콘. **문장 · 버튼 · 애니메이션 없음.** 값이 바뀌면 숫자만 바뀐다 (`drivingScreen="static"`).
- PAUSED: 같은 화면 + 상태 칩 「일시 정차」 + 큰 버튼 [도착 · 개봉] (`state.canArrive` 일 때만 enabled, 아니면 disabled + 「차를 완전히 세우면 열 수 있어요」). `checkingArrival` 이면 버튼 Loading 표시. 누르면 확인 1단계 다이얼로그 「여기서 상자를 열까요?」 [열기][취소] → `onArrive()` → `dispatch(ArriveConfirmed)` (SD-04). 스폰서 카고가 반경 밖이면 버튼 위 한 줄 「목적지 근처가 아니에요 — 지금 열면 일반 리워드」.
- 버튼 높이 ≥ 76dp.

- [ ] **Step 1: 구현** + 프리뷰 (ESCORTING · PAUSED canArrive true/false)
- [ ] **Step 2: 확인** — VC 로 D: 화면이 즉시 호위로 바뀌고 터치 가능한 요소가 없는지 `adb -s emulator-5554 shell uiautomator dump /sdcard/u.xml && adb -s emulator-5554 shell cat /sdcard/u.xml | grep -c 'clickable="true"'` (호위 중 앱 영역 0). P → 일시 정차 · 버튼 활성까지 약 0.4초.
- [ ] **Step 3: Commit** — `feat(ui): 호위 · 일시 정차 화면`

---

### Task 11: SC-05 언박싱 · SC-06 리워드 (+A1 정차 후 요약 · 리플레이)

**Files:**
- Create: `app/src/main/java/com/safecargo/ui/screens/UnboxingScreen.kt`, `RewardScreen.kt`
- Modify: `ui/CargoApp.kt` (분기 + `borderLight` 테두리)

**Interfaces:**
- Consumes: `TripState.result`, `TripResult.summary`, `MockCatalog.reward()`, `TripController.borderLight`
- Produces:
```kotlin
@Composable fun UnboxingScreen(result: TripResult, wc: WindowClass, onFinished: () -> Unit)   // onFinished → dispatch(RevealFinished)
@Composable fun RewardScreen(result: TripResult, reward: Reward?, wc: WindowClass, onClose: () -> Unit) // onClose → dispatch(CloseResult)
```
- 언박싱: Compose 애니메이션(상자 흔들림 → 뚜껑 열림 → 등급 빛) 총 2.5초 후 `onFinished`. 등급 3중 표기 — 색(SC.rare/epic/legendary) + 이름 「레어 · 에픽 · 레전더리」 + 상자 모양 3종(사각 · 리본 · 왕관 장식). 탭하면 바로 건너뜀. 진행 상태는 `revealed` 뿐이라 창 크기 변경 · 재시작 시 처음부터 다시 재생된다 (허용). Lottie 는 디자이너 에셋이 오면 이 화면만 교체 — 지금은 의존성을 넣지 않는다.
- 리워드: 등급 배지 · 열쇠 `keys` · 버프 여부(`familyBuff` 면 「패밀리 버프 ×1.5」, `beltVerified` 아닐 때 "벨트" 단어 금지) · 리워드 카드(title + 「예시」, amountLabel + 「예시」, partnerLabel + 「예시」) · 「시연용 가상 보상 · 실제 사용 불가」 · `!arrivalVerified` 이고 데일리가 아니면 「도착 인증 없음」 · **「이번 호위」 요약** 「깜빡이 먼저 N회 · 깜빡이 없이 M회 · 퍼펙트 K회 · 부드러운 회전 J회」 · **리플레이**: `summary.moments` 를 시간순 아이콘 줄로 놓고 3초 동안 하나씩 하이라이트(다시 보기 버튼) · [닫기] → `onClose`.
- `borderLight` 가 null 아니면 `CargoApp` 전체 테두리에 해당 등급 색 6dp (조명 대체).

- [ ] **Step 1: 구현** + 프리뷰 (등급 3종 · 인증 있음/없음 · 데일리)
- [ ] **Step 2: 확인** — Task 7 Step 2 흐름을 화면으로: 개봉 → 언박싱 → 리워드 요약 숫자가 로그 판정 수와 일치, 닫기 → 로비 스트릭 1. 개봉 중 VC D → 즉시 호위 화면, VC 조명 원래 색. 앱을 홈으로 보낸 상태(개봉 중) → 조명 원복.
- [ ] **Step 3: Commit + push** — `feat(ui): 언박싱 · 리워드 · 이번 호위 요약`

---

### Task 12: 시연 스크립트 · 12개 창 점검 · release APK

**Files:**
- Create: `scripts/s1-demo.sh`, `README.md`

**Interfaces:**
- Consumes: 앱 전체, `poc/scripts/vc-scenario.sh` (heartbeat · `put` · `controls` 함수)

- `s1-demo.sh [dest]` (기본 `cafe`): forward 8088 → heartbeat → 앱 디버그 리셋은 하지 않음 → `am start --es dest <dest>` → 탑승(운전석 + 뒷좌석 1) → D 30km/h → 깜빡이 좌 1.5초 뒤 -90/-180 조향 → 복귀 → 깜빡이 우 1초 뒤 +120 → 복귀 → 감속 → `adb -s emulator-5554 emu geo fix <lng> <lat>` (목적지 좌표, 경도 먼저) → P → 10초 대기 → 종료. 목적지 좌표는 `app/src/main/assets/mock/catalog.json` 에서 `python3 -I -c` 로 읽는다.
- README: 빌드 · 설치 · 시연 방법, 권한 9개와 용도 표, Mock · 「예시」 고지, 위치 원칙(정차 시 1회, 저장 · 전송 없음), Pretendard OFL 고지. 실명 · 로컬 경로 금지.

- [ ] **Step 1: 스크립트 작성 · 1회 실행** — Expected: 앱이 로비 → 호위 → 언박싱 → 리워드(「베이커리 카페 예시」 쿠폰, 인증됨)까지 자동 진행.
- [ ] **Step 2: 3회 연속** — 각 회차 사이 리워드 [닫기]만 누르고 반복. Expected: 3회 모두 완주, `adb -s emulator-5554 logcat -d | grep -E "FATAL|ANR"` 0줄, P → 언박싱 화면 전환 1초 이내(로그 시각 차).
- [ ] **Step 3: 12개 창** — Android Studio 프리뷰 또는 `./gradlew :app:compileDebugKotlin` 성공 + 프리뷰 렌더 확인. 에뮬레이터에서 가로 단독 · 내비와 세로 분할 두 상태를 실제로 확인.
- [ ] **Step 4: release** — `./gradlew :app:assembleRelease`, `adb -s emulator-5554 uninstall com.safecargo.app`, release APK 설치, `s1-demo.sh` 1회 완주. `aapt dump permissions` 결과가 권한 9개와 정확히 일치.
- [ ] **Step 5: Commit + push** — `feat: S1 시연 스크립트 · README`, push.
