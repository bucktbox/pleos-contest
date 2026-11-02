package com.safecargo.data

import com.safecargo.engine.JudgeConfig
import kotlinx.serialization.json.Json

val SafeCargoJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ConfigLoader {
    fun parse(json: String): JudgeConfig = SafeCargoJson.decodeFromString(JudgeConfig.serializer(), json)
}
