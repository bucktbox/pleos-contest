package com.safecargo.engine

import com.safecargo.engine.TripEffect.*
import org.junit.Assert.*
import org.junit.Test

class TripStateMachineTest {
    private val dest = Destination("d1", "파주 베이커리 카페 예시", "주소", 37.0, 127.0, "coupon-cafe")
    private val daily = Cargo("daily", "데일리 카고", CargoKind.DAILY)
    private var n = 0
    private val m = TripStateMachine(JudgeConfig(), daily, { "r${++n}" }, { 20000L })
    private val near = GeoFix(37.0005, 127.0, 800)
    private val far = GeoFix(37.1, 127.0, 800)
    private var t = 0L

    private fun gear(g: Gear) = TripEvent.Signal(VehicleSignal.GearChanged(g, t++))
    private fun ux(b: Boolean) = TripEvent.Signal(VehicleSignal.UxChanged(b, t++))
    private fun select() = TripEvent.SelectDestination(dest)

    private fun TripState.on(vararg e: TripEvent): Step {
        var step = Step(this)
        for (ev in e) step = m.reduce(step.state, ev)
        return step
    }

    @Test fun idle_drive_autoloads_daily_and_escorts() {
        val s = TripState().on(gear(Gear.DRIVE))
        assertEquals(Phase.ESCORTING, s.state.phase)
        assertEquals("daily", s.state.cargo?.id)
        assertTrue(s.effects.any { it is PersistActive })
    }

    @Test fun select_destination_loads_sponsor_and_requests_route() {
        val s = TripState().on(select())
        assertEquals(Phase.LOADED, s.state.phase)
        assertEquals(CargoKind.SPONSOR, s.state.cargo?.kind)
        assertEquals(2, s.effects.size)
        assertEquals(RequestRoute(dest), s.effects[0])
        assertTrue(s.effects[1] is PersistActive)
    }

    @Test fun park_with_sponsor_requests_location() {
        val s = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK))
        assertEquals(Phase.PAUSED, s.state.phase)
        assertTrue(s.state.checkingArrival)
        assertTrue(s.effects.contains(RequestLocation))
    }

    @Test fun inside_radius_opens_only_after_ux_clears() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(near))
        assertEquals(Phase.PAUSED, a.state.phase)
        assertTrue(a.state.autoOpenPending)
        val b = a.state.on(ux(false))
        assertEquals(Phase.OPENING, b.state.phase)
        assertTrue(b.state.result!!.arrivalVerified)
        assertEquals("coupon-cafe", b.state.result!!.rewardId)
        assertTrue(b.effects.first() is PersistActive)
        assertTrue(b.effects[1] is SaveResult)
        assertTrue(b.effects.any { it is FlashLights })
    }

    @Test fun outside_radius_stays_paused_and_can_arrive() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(far))
        assertEquals(Phase.PAUSED, a.state.phase)
        assertFalse(a.state.arrivalVerified)
        assertTrue(a.state.on(ux(false)).state.canArrive)
    }

    @Test fun arrive_on_sponsor_relocates_then_opens_unverified() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(far), ux(false), TripEvent.ArriveConfirmed)
        assertTrue(a.effects.contains(RequestLocation))
        val b = a.state.on(TripEvent.LocationResolved(null))
        assertEquals(Phase.OPENING, b.state.phase)
        assertEquals("general", b.state.result!!.rewardId)
        assertFalse(b.state.result!!.arrivalVerified)
    }

    @Test fun stale_fix_counts_as_failure() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(GeoFix(37.0, 127.0, 61_000)))
        assertFalse(a.state.arrivalVerified)
    }

    @Test fun daily_park_needs_arrive_tap() {
        val a = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK))
        assertFalse(a.effects.contains(RequestLocation))
        val b = a.state.on(ux(false), TripEvent.ArriveConfirmed)
        assertEquals(Phase.OPENING, b.state.phase)
        assertEquals("daily", b.state.result!!.rewardId)
    }

    @Test fun arrive_ignored_while_ux_restricted() {
        val a = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK))
        val b = a.state.on(TripEvent.ArriveConfirmed)
        assertEquals(a.state, b.state)
        assertTrue(b.effects.isEmpty())
    }

    @Test fun drive_during_opening_restores_and_loads_daily() {
        val a = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK), ux(false), TripEvent.ArriveConfirmed)
        val b = a.state.on(gear(Gear.DRIVE))
        assertTrue(b.effects.contains(RestoreLights))
        assertTrue(b.effects.contains(StopSpeech))
        assertTrue(b.effects.none { it is SaveResult })
        assertEquals(Phase.ESCORTING, b.state.phase)
        assertEquals("daily", b.state.cargo?.id)
        assertEquals("r1", b.state.lastResult?.id)
        assertEquals(0.0, b.state.judge.keys, 0.0)
    }

    @Test fun neutral_and_unknown_do_not_transition() {
        assertEquals(Phase.ESCORTING, TripState().on(gear(Gear.DRIVE), gear(Gear.NEUTRAL)).state.phase)
        val p = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK), ux(false), gear(Gear.UNKNOWN))
        assertEquals(Phase.PAUSED, p.state.phase)
        assertFalse(p.state.canArrive)
    }

    @Test fun late_location_after_redrive_is_ignored() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), gear(Gear.DRIVE), TripEvent.LocationResolved(near))
        assertEquals(Phase.ESCORTING, a.state.phase)
        assertTrue(a.effects.isEmpty())
    }

    @Test fun steering_ignored_outside_escorting() {
        val a = TripState().on(
            select(),
            TripEvent.Signal(VehicleSignal.TurnChanged(TurnSignal.LEFT, 1)),
            TripEvent.Signal(VehicleSignal.SteerChanged(-100f, 2)),
        )
        assertEquals(Phase.LOADED, a.state.phase)
        assertEquals(JudgeState(), a.state.judge)
    }

    @Test fun buff_uses_latest_seats() {
        val seats = SeatSnapshot(occupied = mapOf(Seat.DRIVER to true, Seat.REAR_LEFT to true))
        val a = TripState().on(gear(Gear.DRIVE), TripEvent.Signal(VehicleSignal.SeatsChanged(seats, 5)), gear(Gear.PARK), ux(false), TripEvent.ArriveConfirmed)
        assertTrue(a.state.result!!.familyBuff)
    }

    @Test fun restore_opening_keeps_result_id() {
        val opened = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK), ux(false), TripEvent.ArriveConfirmed).state
        val r1 = opened.result!!
        val b = TripState().on(TripEvent.Restore(opened))
        assertEquals("r1", b.state.result!!.id)
        assertEquals(listOf<TripEffect>(SaveResult(r1)), b.effects)
        assertEquals(1, n) // Restore 는 id 생성기를 호출하지 않음
    }

    @Test fun restore_resets_transient_fields() {
        val b = TripState().on(gear(Gear.PARK), ux(false))
        val c = TripState().on(TripEvent.Restore(b.state))
        assertEquals(Gear.UNKNOWN, c.state.gear)
        assertTrue(c.state.uxRestricted)
    }

    @Test fun close_result_returns_idle_with_last_result() {
        val a = TripState().on(gear(Gear.DRIVE), gear(Gear.PARK), ux(false), TripEvent.ArriveConfirmed, TripEvent.CloseResult)
        assertEquals(Phase.IDLE, a.state.phase)
        assertEquals("r1", a.state.lastResult?.id)
        assertTrue(a.effects.contains(RestoreLights))
        assertTrue(a.effects.contains(ClearActive))
    }

    @Test fun arrive_then_neutral_before_location_does_not_open() {
        val a = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(far), ux(false),
            TripEvent.ArriveConfirmed, gear(Gear.NEUTRAL), TripEvent.LocationResolved(null))
        assertEquals(Phase.PAUSED, a.state.phase)
        val b = a.state.on(gear(Gear.PARK))
        assertEquals(Phase.OPENING, b.state.phase)
        assertEquals("general", b.state.result!!.rewardId)
    }

    @Test fun restore_paused_checking_requests_location_again() {
        val sponsor = Cargo("cargo-d1", "x", CargoKind.SPONSOR, dest)
        val b = TripState().on(TripEvent.Restore(TripState(phase = Phase.PAUSED, cargo = sponsor, checkingArrival = true)))
        assertEquals(listOf<TripEffect>(RequestLocation), b.effects)
    }

    @Test fun reset_keeps_live_vehicle_fields() {
        val a = TripState().on(gear(Gear.PARK), ux(false), TripEvent.Reset)
        assertEquals(Phase.IDLE, a.state.phase)
        assertEquals(Gear.PARK, a.state.gear)
        assertFalse(a.state.uxRestricted)
        assertTrue(a.state.connected)
        assertEquals(listOf<TripEffect>(RestoreLights, ClearActive), a.effects)
    }

    private fun steer(deg: Float, at: Long) = TripEvent.Signal(VehicleSignal.SteerChanged(deg, at))
    private fun turn(sig: TurnSignal, at: Long) = TripEvent.Signal(VehicleSignal.TurnChanged(sig, at))

    @Test fun scoring_turn_during_escort_persists_judge_progress() {
        val esc = TripState().on(select(), gear(Gear.DRIVE)).state
        var step = Step(esc)
        val evs = listOf(turn(TurnSignal.LEFT, 1000), steer(-30f, 2500), steer(-90f, 3000), steer(-180f, 4000), steer(0f, 5000))
        val persisted = mutableListOf<TripState>()
        for (ev in evs) {
            step = m.reduce(step.state, ev)
            step.effects.filterIsInstance<PersistActive>().forEach { persisted += it.state }
        }
        assertTrue(step.state.judge.keys > 0.0)
        assertTrue(persisted.any { it.judge.keys > 0.0 })
        assertEquals(step.state.judge.keys, persisted.last().judge.keys, 0.0)
    }

    @Test fun steer_sample_without_persisted_change_emits_no_persist() {
        val esc = TripState().on(select(), gear(Gear.DRIVE)).state
        val a = m.reduce(esc, steer(-5f, 1000))
        val b = m.reduce(a.state, steer(-8f, 1100))
        assertTrue(b.effects.none { it is PersistActive })
        val c = m.reduce(b.state, ux(false))
        assertTrue(c.effects.none { it is PersistActive })
    }

    @Test fun arrival_check_distinguishes_no_fix_from_out_of_range() {
        val parked = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK)).state
        assertEquals(ArrivalCheck.NONE, parked.arrivalCheck)
        assertEquals(ArrivalCheck.OUT_OF_RANGE, parked.on(TripEvent.LocationResolved(far)).state.arrivalCheck)
        assertEquals(ArrivalCheck.NO_FIX, parked.on(TripEvent.LocationResolved(null)).state.arrivalCheck)
        assertEquals(ArrivalCheck.NO_FIX, parked.on(TripEvent.LocationResolved(GeoFix(37.0, 127.0, 61_000))).state.arrivalCheck)
        assertEquals(ArrivalCheck.VERIFIED, parked.on(TripEvent.LocationResolved(near)).state.arrivalCheck)
    }

    @Test fun arrival_check_resets_on_new_check_and_is_persisted() {
        val out = TripState().on(select(), gear(Gear.DRIVE), gear(Gear.PARK), TripEvent.LocationResolved(far))
        assertTrue(out.effects.any { it is PersistActive && it.state.arrivalCheck == ArrivalCheck.OUT_OF_RANGE })
        // 다시 달렸다 세우면 새 확인 — 이전 결과는 지운다
        assertEquals(ArrivalCheck.NONE, out.state.on(gear(Gear.DRIVE), gear(Gear.PARK)).state.arrivalCheck)
        // [도착 · 개봉] 의 재확인도 NONE 에서 시작
        assertEquals(ArrivalCheck.NONE, out.state.on(ux(false), TripEvent.ArriveConfirmed).state.arrivalCheck)
    }
}
