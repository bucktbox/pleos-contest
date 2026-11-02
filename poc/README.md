# SafeCargo POC (버리는 진단 앱)

Pleos SDK 신호를 시각과 함께 logcat `SCPOC` 에 기록한다. 결과: `docs/reference/2026-11-02-poc-sdk-검증결과.md`

```bash
./gradlew assembleDebug
adb -s emulator-5554 install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 forward tcp:8088 tcp:8088      # Virtual Console
adb -s emulator-5554 shell am start -S -n com.safecargo.poc/.MainActivity   # --ez route true : 경로 요청 시험
scripts/vc-scenario.sh                               # S1 흐름 자동 재현
adb -s emulator-5554 logcat -s SCPOC:I
```

에뮬레이터: Pleos Connect v2.1.0, **Cold boot 필수**. 본 앱 코드는 여기서 가져가지 않는다 (설계 승인 후 새로 작성).
