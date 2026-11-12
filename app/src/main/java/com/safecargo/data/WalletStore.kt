package com.safecargo.data

import java.io.File

/** 보상함 · 토큰 원장 저장 (FR-DATA-004). 차속 · 거친 운전 이벤트는 저장하지 않는다. */
class WalletStore(private val dir: File) {
    private val file get() = File(dir, "wallet.json")

    @Synchronized fun load(): WalletData = try {
        if (file.exists()) SafeCargoJson.decodeFromString(WalletData.serializer(), file.readText()) else WalletData()
    } catch (e: Exception) { WalletData() }

    @Synchronized fun save(w: WalletData) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(SafeCargoJson.encodeToString(WalletData.serializer(), w))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    @Synchronized fun clear() { file.delete() }
}
