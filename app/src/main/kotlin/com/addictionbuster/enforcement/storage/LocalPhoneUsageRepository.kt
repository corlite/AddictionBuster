package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class LocalPhoneUsageRepository(context: Context) {
    private val usageFile = AtomicJsonFile(File(storageDir(context), "phone_usage.json"))

    @Synchronized
    fun load(dateKey: String = today()): PhoneUsageState =
        readAll()[dateKey] ?: PhoneUsageState.empty(dateKey)

    @Synchronized
    fun save(state: PhoneUsageState) {
        val all = readAll().toMutableMap()
        all[state.dateKey] = state
        writeAll(all)
    }

    @Synchronized
    fun addUsage(phoneUsageMillis: Long, dateKey: String = today()): PhoneUsageState {
        if (phoneUsageMillis < 0L) {
            throw InvalidEnforcementContextException("phoneUsageMillis must be >= 0")
        }
        val current = load(dateKey)
        val updated = current.copy(
            dailyUsedMillis = current.dailyUsedMillis + phoneUsageMillis,
            sessionUsedMillis = current.sessionUsedMillis + phoneUsageMillis
        )
        save(updated)
        return updated
    }

    @Synchronized
    fun resetSession(dateKey: String = today()): PhoneUsageState {
        val current = load(dateKey)
        val updated = current.copy(sessionUsedMillis = 0L)
        save(updated)
        return updated
    }

    @Synchronized
    fun markOfflineGapPending(durationMillis: Long, dateKey: String = today()): PhoneUsageState {
        if (durationMillis < 0L) {
            throw InvalidEnforcementContextException("durationMillis must be >= 0")
        }
        val current = load(dateKey)
        val updated = current.copy(
            pendingOfflineGapMillis = current.pendingOfflineGapMillis + durationMillis
        )
        save(updated)
        return updated
    }

    private fun readAll(): Map<String, PhoneUsageState> {
        val root = usageFile.readObjectOrNull() ?: return emptyMap()
        val array = root.optJSONArray("days")
            ?: throw InvalidEnforcementContextException("PhoneUsage file missing days array")
        return buildMap {
            for (index in 0 until array.length()) {
                val state = array.getJSONObject(index).toPhoneUsageState()
                put(state.dateKey, state)
            }
        }
    }

    private fun writeAll(states: Map<String, PhoneUsageState>) {
        val array = JSONArray()
        states.values.forEach { array.put(it.toJson()) }
        usageFile.writeObject(JSONObject().put("days", array))
    }

    private fun today(): String = LocalDate.now().toString()
}

data class PhoneUsageState(
    val dateKey: String,
    val dailyUsedMillis: Long,
    val sessionUsedMillis: Long,
    val pendingOfflineGapMillis: Long
) {
    init {
        if (dateKey.isBlank()) throw InvalidEnforcementContextException("dateKey is required")
        if (dailyUsedMillis < 0L) throw InvalidEnforcementContextException("dailyUsedMillis must be >= 0")
        if (sessionUsedMillis < 0L) throw InvalidEnforcementContextException("sessionUsedMillis must be >= 0")
        if (pendingOfflineGapMillis < 0L) {
            throw InvalidEnforcementContextException("pendingOfflineGapMillis must be >= 0")
        }
    }

    companion object {
        fun empty(dateKey: String): PhoneUsageState =
            PhoneUsageState(
                dateKey = dateKey,
                dailyUsedMillis = 0L,
                sessionUsedMillis = 0L,
                pendingOfflineGapMillis = 0L
            )
    }
}

private fun PhoneUsageState.toJson(): JSONObject =
    JSONObject()
        .put("dateKey", dateKey)
        .put("dailyUsedMillis", dailyUsedMillis)
        .put("sessionUsedMillis", sessionUsedMillis)
        .put("pendingOfflineGapMillis", pendingOfflineGapMillis)

private fun JSONObject.toPhoneUsageState(): PhoneUsageState =
    PhoneUsageState(
        dateKey = getString("dateKey"),
        dailyUsedMillis = getLong("dailyUsedMillis"),
        sessionUsedMillis = getLong("sessionUsedMillis"),
        pendingOfflineGapMillis = getLong("pendingOfflineGapMillis")
    )
