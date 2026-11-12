---
doc: 미결 확인 요청
service: 세이프카고
title: 차량용 앱 개발 공모전 — 개발 규정 확인 요청
version: "1.0"
version_note: 초안
created: 2026-10-29
updated: 2026-10-29
author: 세이프 카고 팀 (PM)
status: 초안
---

# 1. 보내는 곳 · 방법

- 받는 곳: 공모전 운영 사무국 — appdevchallenge@hyundai-partners.com
- 제목(제안): 「[차량용 앱 개발 공모전] 개발 규정 문의 — Vehicle SDK 기어 · 주행 제한 · 퍼미션」
- 보내는 시점: 10.30 (회신 희망 11.01 — 팀 SDK 검증 일정)
- 익명 규정: 메일 본문 · 첨부에 성명 · 소속을 넣지 않는다 (참가 신청서는 별도 제출). 앱 이름만 쓴다.
- 받은 회신은 PRD `--update` 의 입력 자료(협의 결과)로 올린다.

# 2. 메일 본문 (붙여 넣기용)

안녕하세요. 차량용 앱 개발 공모전에 출품을 준비 중인 팀입니다.
Pleos Connect SDK 문서(document.pleos.ai, Vehicle 2.3.2 · NaviHelper 3.0.5 · TextToSpeech 3.3.1 · FusedLocation 1.1.3)와 Emulator 2.1.0 기준으로 개발하고 있으며, 문서에서 찾지 못한 아래 5가지를 여쭙니다.

1. **기어(P/R/N/D) 상태를 받는 방법** — Vehicle SDK 2.3.2 의 도메인 목록에 Gear 가 없고, `Vehicle` 클래스 예제에만 `getGear().registerGearSelection` 이 남아 있습니다. Virtual Console 에는 「Gear Selection」 카드가 있습니다. 기어 상태는 (a) 앞으로 Vehicle SDK 에 추가될 예정인지, (b) AAOS 표준 `CarPropertyManager` 의 기어 속성으로 받아야 하는지, (c) `CarUxRestrictionsManager` 의 주행 제한 상태로 대신해야 하는지 알려 주시면 감사하겠습니다. 표준 API 를 쓴다면 필요한 퍼미션도 함께 부탁드립니다.
2. **주행 제한(DO) 선언 방법 · 승인** — 「DO 미선언 시 Drive 사용 불가」와 `CarUxRestrictionsManager` 적용 방법은 확인했습니다. DO 를 선언하는 방법(Manifest 메타데이터 등), 공모전 출품작에 별도 승인이 필요한지, 주행 중 Foreground Service 가 유지되는지, 주행 중 TTS 음성 · 짧은 효과음이 허용되는지 궁금합니다.
3. **앰비언트 조명 제어 퍼미션** — `setAmbientLightBrightnessColor` 의 API Reference 는 `CAR_LIGHTS`, 퍼미션 가이드는 제어(set)에 `CONTROL_CAR_*` 를 안내합니다. 어느 퍼미션을 선언해야 하는지, 공모전 출품작에서 사용이 승인되는지 확인 부탁드립니다.
4. **심사용 GitHub 계정** — 비공개 저장소 제출 시 초대할 심사용 계정이 언제 공지되는지 알려 주세요.
5. **외부 서버 · 로그인 정책** — 출시 단계에서 랭킹용 외부 서버와 간단한 로그인을 쓰려 합니다. Pleos 앱 정책상 제한이 있는지 궁금합니다. (제출본은 서버 없이 동작합니다.)

10.28 온라인 설명회 Q&A 에서 이미 답하신 내용이면 다시보기 · 자료 위치만 알려 주셔도 됩니다. 감사합니다.

세이프 카고 팀 드림

# 3. 항목별 현재 잠정안 · 회신란

| 문의 | PRD 항목 | 현재 잠정안 (PRD 에 이렇게 쓰여 있다) | 확인해 줄 것 | 회신 기한 | 회신 |
|---|---|---|---|---|---|
| 1 | AG-03 | `CarUxRestrictionsManager` 주행 제한 상태로 주행 · 정차 판정 + POC 에서 AAOS 기어 속성 시험 | 기어 상태를 받는 공식 방법 · 퍼미션 | 11.01 | |
| 2 | U-01 | DO 선언 + 주행 중 화면은 게이지만 · 음성 코칭 | DO 선언 방법 · 승인 · Foreground Service · 주행 중 TTS · 효과음 | 11.01 | |
| 3 | AG-05 | `CAR_LIGHTS` · `CONTROL_CAR_LIGHTS` 둘 다 선언, 실패 시 화면 효과 | 선언할 퍼미션 · 승인 여부 | 11.05 | |
| 4 | U-06 | 공지 대기 | 심사용 GitHub 계정 · 공지 시점 | 11.13 | |
| 5 | U-07 | 제출본은 서버 없음 | 외부 서버 · 로그인 제한 | 11.23 | |
