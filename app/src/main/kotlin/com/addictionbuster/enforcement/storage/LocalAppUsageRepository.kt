package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class LocalAppUsageRepository(context: Context) {
    private val usageFile = AtomicJsonFile(File(storageDir(context), "app_usage.json"))

    @Synchronized
    fun load(identityKey: String, dateKey: String = today()): AppUsageState =
        readAll()[identityKey]?.takeIf { it.dateKey == dateKey }
            ?: AppUsageState.empty(identityKey, dateKey)

    @Synchronized
    fun save(state: AppUsageState) {
        val all = readAll().toMutableMap()
        all[state.identityKey] = state
        writeAll(all)
    }

    @Synchronized
    fun addUsage(
        identityKey: String,
        appUsageMillis: Long,
        dateKey: String = today()
    ): AppUsageState {
        if (appUsageMillis < 0L) {
            throw InvalidEnforcementContextException("appUsageMillis must be >= 0")
        }
        val current = load(identityKey, dateKey)
        val updated = current.copy(
            usedMillis = current.usedMillis + appUsageMillis,
            sessionUsedMillis = current.sessionUsedMillis + appUsageMillis,
            continuousUsedMillis = current.continuousUsedMillis + appUsageMillis
        )
        save(updated)
        return updated
    }

    @Synchronized
    fun incrementOpen(identityKey: String, openedAtMillis: Long, dateKey: String = today()): AppUsageState {
        if (openedAtMillis < 0L) {
            throw InvalidEnforcementContextException("openedAtMillis must be >= 0")
        }
        val current = load(identityKey, dateKey)
        val updated = current.copy(
            openCount = current.openCount + 1,
            lastOpenedAtMillis = openedAtMillis
        )
        save(updated)
        return updated
    }

    @Synchronized
    fun resetSession(identityKey: String, dateKey: String = today()): AppUsageState {
        val current = load(identityKey, dateKey)
        val updated = current.copy(
            sessionUsedMillis = 0L,
            continuousUsedMillis = 0L
        )
        save(updated)
        return updated
    }

    @Synchronized
    fun markOfflineGapPending(
        identityKey: String,
        durationMillis: Long,
        dateKey: String = today()
    ): AppUsageState {
        if (durationMillis < 0L) {
            throw InvalidEnforcementContextException("durationMillis must be >= 0")
        }
        val current = load(identityKey, dateKey)
        val updated = current.copy(
            pendingOfflineGapMillis = current.pendingOfflineGapMillis + durationMillis
        )
        save(updated)
        return updated
    }

    private fun readAll(): Map<String, AppUsageState> {
        val root = usageFile.readObjectOrNull() ?: return emptyMap()
        val array = root.optJSONArray("apps")
            ?: throw InvalidEnforcementContextException("AppUsage file missing apps array")
        return buildMap {
            for (index in 0 until array.length()) {
                val state = array.getJSONObject(index).toAppUsageState()
                put(state.identityKey, state)
            }
        }
    }

    private fun writeAll(states: Map<String, AppUsageState>) {
        val array = JSONArray()
        states.values.forEach { array.put(it.toJson()) }
        usageFile.writeObject(JSONObject().put("apps", array))
    }

    private fun today(): String = LocalDate.now().toString()
}

data class AppUsageState(
    val identityKey: String,
    val dateKey: String,
    val usedMillis: Long,
    val sessionUsedMillis: Long,
    val continuousUsedMillis: Long,
    val openCount: Int,
    val lastOpenedAtMillis: Long,
    val pendingOfflineGapMillis: Long
) {
    init {
        if (identityKey.isBlank()) throw InvalidEnforcementContextException("identityKey is required")
        if (dateKey.isBlank()) throw InvalidEnforcementContextException("dateKey is required")
        if (usedMillis < 0L) throw InvalidEnforcementContextException("usedMillis must be >= 0")
        if (sessionUsedMillis < 0L) throw InvalidEnforcementContextException("sessionUsedMillis must be >= 0")
        if (continuousUsedMillis < 0L) throw InvalidEnforcementContextException("continuousUsedMillis must be >= 0")
        if (openCount < 0) throw InvalidEnforcementContextException("openCount must be >= 0")
        if (lastOpenedAtMillis < 0L) throw InvalidEnforcementContextException("lastOpenedAtMillis must be >= 0")
        if (pendingOfflineGapMillis < 0L) {
            throw InvalidEnforcementContextException("pendingOfflineGapMillis must be >= 0")
        }
    }

    companion object {
        fun empty(identityKey: String, dateKey: String): AppUsageState =
            AppUsageState(
                identityKey = identityKey,
                dateKey = dateKey,
                usedMillis = 0L,
                sessionUsedMillis = 0L,
                continuousUsedMillis = 0L,
                openCount = 0,
                lastOpenedAtMillis = 0L,
                pendingOfflineGapMillis = 0L
            )
    }
}

private fun AppUsageState.toJson(): JSONObject =
    JSONObject()
        .put("identityKey", identityKey)
        .put("dateKey", dateKey)
        .put("usedMillis", usedMillis)
        .put("sessionUsedMillis", sessionUsedMillis)
        .put("continuousUsedMillis", continuousUsedMillis)
        .put("openCount", openCount)
        .put("lastOpenedAtMillis", lastOpenedAtMillis)
        .put("pendingOfflineGapMillis", pendingOfflineGapMillis)

private fun JSONObject.toAppUsageState(): AppUsageState =
    AppUsageState(
        identityKey = getString("identityKey"),
        dateKey = getString("dateKey"),
        usedMillis = getLong("usedMillis"),
        sessionUsedMillis = getLong("sessionUsedMillis"),
        continuousUsedMillis = getLong("continuousUsedMillis"),
        openCount = getInt("openCount"),
        lastOpenedAtMillis = getLong("lastOpenedAtMillis"),
        pendingOfflineGapMillis = getLong("pendingOfflineGapMillis")
    )
