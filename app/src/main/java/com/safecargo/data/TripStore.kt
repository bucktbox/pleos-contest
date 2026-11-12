package com.safecargo.data

import com.safecargo.engine.TripResult
import com.safecargo.engine.TripState
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

class TripStore(private val dir: File) {
    private val activeFile get() = File(dir, "active_trip.json")
    private val tripsFile get() = File(dir, "trips.json")
    private val listSer = ListSerializer(TripResult.serializer())

    @Synchronized fun saveActive(s: TripState) =
        write(activeFile, SafeCargoJson.encodeToString(TripState.serializer(), s))

    @Synchronized fun loadActive(): TripState? = try {
        if (activeFile.exists()) SafeCargoJson.decodeFromString(TripState.serializer(), activeFile.readText()) else null
    } catch (e: Exception) { null }

    @Synchronized fun clearActive() { activeFile.delete() }

    @Synchronized fun appendResult(r: TripResult): Boolean {
        val cur = results()
        if (cur.any { it.id == r.id }) return false
        write(tripsFile, SafeCargoJson.encodeToString(listSer, cur + r))
        return true
    }

    @Synchronized fun results(): List<TripResult> = try {
        if (tripsFile.exists()) SafeCargoJson.decodeFromString(listSer, tripsFile.readText()) else emptyList()
    } catch (e: Exception) { emptyList() }

    @Synchronized fun clearAll() { activeFile.delete(); tripsFile.delete() }

    private fun write(f: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
