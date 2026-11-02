package com.safecargo.engine

import com.safecargo.engine.TripEffect.*

class TripStateMachine(
    private val config: JudgeConfig,
    private val dailyCargo: Cargo,
    private val newId: () -> String,
    private val todayEpochDay: () -> Long,
) {
    fun reduce(s: TripState, e: TripEvent): Step {
        val base = step(s, e)
        // PAUSED 에서 자동 개봉 조건 (매 이벤트 뒤)
        val st = base.state
        val out = if (st.phase == Phase.PAUSED && st.autoOpenPending && st.gear == Gear.PARK && !st.uxRestricted) {
            val o = open(st)
            Step(o.state, base.effects + o.effects)
        } else base
        return withPersist(s, e, out)
    }

    /**
     * 활성 운행에서 저장 대상 상태가 바뀌었는데 그 상태의 PersistActive 가 없으면 덧붙인다
     * (판정 진행 · 좌석 · 도착 확인 등). 일시값(기어 · UX · 연결 · 원시 조향 샘플)만 바뀐 경우는 저장하지 않는다.
     * Restore 는 이미 디스크에 있는 상태라 제외한다.
     */
    private fun withPersist(old: TripState, e: TripEvent, out: Step): Step {
        val n = out.state
        if (e is TripEvent.Restore || n.phase == Phase.IDLE) return out
        if (persisted(old) == persisted(n)) return out
        if (out.effects.any { it is PersistActive && it.state == n }) return out
        return Step(n, out.effects + PersistActive(n))
    }

    private fun persisted(s: TripState): TripState = s.copy(
        gear = Gear.UNKNOWN, uxRestricted = true, connected = false,
        judge = s.judge.copy(lastDeg = null, lastAt = null, peakRate = 0f),
    )

    private fun isDrive(g: Gear) = g == Gear.DRIVE || g == Gear.REVERSE

    private fun step(s0: TripState, e: TripEvent): Step = when (e) {
        is TripEvent.Signal -> onSignal(s0, e.signal)
        is TripEvent.SelectDestination -> {
            if (s0.phase == Phase.IDLE || s0.phase == Phase.LOADED) {
                val d = e.destination
                val n = s0.copy(
                    phase = Phase.LOADED,
                    cargo = Cargo("cargo-" + d.id, d.name + " 카고", CargoKind.SPONSOR, d),
                )
                Step(n, listOf(RequestRoute(d), PersistActive(n)))
            } else Step(s0)
        }
        is TripEvent.LocationResolved -> {
            if (s0.phase == Phase.PAUSED && s0.checkingArrival) {
                val fix = e.fix
                val dest = s0.cargo?.destination
                val ok = fix != null && dest != null &&
                    fix.ageMs <= config.locationMaxAgeSec * 1000L &&
                    distanceM(fix.lat, fix.lng, dest.lat, dest.lng) <= config.arrivalRadiusM
                val n = s0.copy(checkingArrival = false, arrivalVerified = ok, openAfterLocation = false)
                // 개봉은 reduce 의 autoOpen 검사가 gear==PARK && !ux 일 때만 수행
                Step(if (ok || s0.openAfterLocation) n.copy(autoOpenPending = true) else n)
            } else Step(s0)
        }
        TripEvent.ArriveConfirmed -> {
            if (!s0.canArrive) Step(s0)
            else if (s0.cargo?.kind == CargoKind.SPONSOR) {
                Step(s0.copy(checkingArrival = true, openAfterLocation = true), listOf(RequestLocation))
            } else open(s0)
        }
        TripEvent.RevealFinished -> {
            if (s0.phase == Phase.OPENING) {
                val n = s0.copy(revealed = true)
                Step(n, listOf(PersistActive(n)))
            } else Step(s0)
        }
        TripEvent.CloseResult -> {
            if (s0.phase == Phase.OPENING) {
                val n = TripState(
                    lastResult = s0.result, seats = s0.seats,
                    gear = s0.gear, uxRestricted = s0.uxRestricted, connected = s0.connected,
                )
                Step(n, listOf(RestoreLights, ClearActive))
            } else Step(s0)
        }
        is TripEvent.Restore -> {
            val n = e.saved.copy(gear = Gear.UNKNOWN, uxRestricted = true, connected = false)
            val r = n.result
            Step(n, when {
                n.phase == Phase.OPENING && r != null -> listOf(SaveResult(r))
                n.phase == Phase.PAUSED && n.checkingArrival -> listOf(RequestLocation)
                else -> emptyList()
            })
        }
        TripEvent.Reset -> Step(
            TripState(gear = s0.gear, uxRestricted = s0.uxRestricted, seats = s0.seats, connected = s0.connected),
            listOf(RestoreLights, ClearActive))
    }

    private fun onSignal(s0: TripState, sig: VehicleSignal): Step {
        var s = when (sig) {
            is VehicleSignal.GearChanged -> s0.copy(gear = sig.gear, connected = true)
            is VehicleSignal.UxChanged -> s0.copy(uxRestricted = sig.restricted, connected = true)
            is VehicleSignal.SeatsChanged -> s0.copy(seats = sig.seats, connected = true)
            is VehicleSignal.Disconnected -> s0.copy(gear = Gear.UNKNOWN, uxRestricted = true, connected = false)
            else -> s0.copy(connected = true)
        }
        when (sig) {
            is VehicleSignal.TurnChanged ->
                if (s.phase == Phase.ESCORTING) s = s.copy(judge = JudgeEngine.onTurn(s.judge, sig.signal, sig.atMs, config))
            is VehicleSignal.SteerChanged ->
                if (s.phase == Phase.ESCORTING) s = s.copy(judge = JudgeEngine.onSteer(s.judge, sig.deg, sig.atMs, config))
            is VehicleSignal.GearChanged -> return onGear(s, sig.gear)
            else -> {}
        }
        return Step(s)
    }

    private fun onGear(s: TripState, g: Gear): Step {
        if (isDrive(g)) {
            return when (s.phase) {
                Phase.IDLE -> escort(s.copy(cargo = dailyCargo, judge = JudgeState()))
                Phase.LOADED -> escort(s.copy(judge = JudgeState()))
                Phase.PAUSED -> escort(s.copy(checkingArrival = false, openAfterLocation = false, autoOpenPending = false))
                Phase.OPENING -> {
                    val n = s.copy(
                        cargo = dailyCargo, judge = JudgeState(), lastResult = s.result, result = null,
                        revealed = false, arrivalVerified = false, checkingArrival = false,
                        openAfterLocation = false, autoOpenPending = false,
                    ).copy(phase = Phase.ESCORTING)
                    Step(n, listOf(RestoreLights, StopSpeech, PersistActive(n)))
                }
                Phase.ESCORTING -> Step(s)
            }
        }
        if (g == Gear.PARK && s.phase == Phase.ESCORTING) {
            return if (s.cargo?.kind == CargoKind.SPONSOR) {
                val n = s.copy(phase = Phase.PAUSED, checkingArrival = true)
                Step(n, listOf(RequestLocation, PersistActive(n)))
            } else {
                val n = s.copy(phase = Phase.PAUSED)
                Step(n, listOf(PersistActive(n)))
            }
        }
        return Step(s)
    }

    private fun escort(s: TripState): Step {
        val n = s.copy(phase = Phase.ESCORTING)
        return Step(n, listOf(PersistActive(n)))
    }

    private fun open(s: TripState): Step {
        val o = JudgeEngine.finalize(s.judge, s.seats, config)
        val daily = s.cargo?.kind != CargoKind.SPONSOR
        val dest = s.cargo?.destination
        val rewardId = when {
            daily -> "daily"
            s.arrivalVerified && dest != null -> dest.rewardId
            else -> "general"
        }
        val j = s.judge
        val r = TripResult(
            id = newId(),
            cargoName = s.cargo?.name ?: dailyCargo.name,
            destinationName = dest?.name,
            grade = o.grade, keys = o.finalKeys, familyBuff = o.familyBuff, beltVerified = o.beltVerified,
            arrivalVerified = s.arrivalVerified, rewardId = rewardId, epochDay = todayEpochDay(),
            summary = TripSummary(j.signalFirst, j.noSignalTurns, j.perfect, j.smoothTurns, j.moments),
        )
        val n = s.copy(
            phase = Phase.OPENING, result = r, revealed = false,
            checkingArrival = false, openAfterLocation = false, autoOpenPending = false,
        )
        return Step(n, listOf(PersistActive(n), SaveResult(r), FlashLights(o.grade), AnnounceArrival))
    }
}
