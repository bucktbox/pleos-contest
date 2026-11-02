package com.safecargo.data

import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeConfig
import kotlinx.serialization.json.Json

val SafeCargoJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ConfigLoader {
    /**
     * judge.json 을 읽는다. 읽을 수 없거나 [problems] 가 하나라도 있으면 [onInvalid] 로 이유(값은 빼고)를 알리고
     * 기본값 [JudgeConfig] 로 돌아간다 — 잘못된 설정 하나로 앱이 죽거나 등급이 엉뚱하게 나오지 않게.
     */
    fun parse(json: String, onInvalid: (String) -> Unit = {}): JudgeConfig {
        val c = try {
            SafeCargoJson.decodeFromString(JudgeConfig.serializer(), json)
        } catch (e: IllegalArgumentException) { // SerializationException 포함
            onInvalid("judge.json unreadable (${e.javaClass.simpleName}) — defaults")
            return JudgeConfig()
        }
        val bad = problems(c)
        if (bad.isEmpty()) return c
        onInvalid("judge.json invalid: ${bad.joinToString()} — defaults")
        return JudgeConfig()
    }

    /** 검사 항목 이름 목록 (값은 담지 않는다). 비어 있으면 유효. */
    fun problems(c: JudgeConfig): List<String> = buildList {
        val t = c.gradeThresholds
        // gradeOf 는 등급 수 - 1 개의 경계(에픽 하한, 레전더리 하한)를 쓴다.
        if (t.size != Grade.entries.size - 1) add("gradeThresholds.size")
        else if (t.zipWithNext().any { (a, b) -> a >= b }) add("gradeThresholds.order")
        if (!Grade.entries.all { it in c.gradeLightColors }) add("gradeLightColors.missing")
        if (c.perfectMultiplier <= 0.0) add("perfectMultiplier")
        if (c.familyBuffMultiplier <= 0.0) add("familyBuffMultiplier")
    }
}
