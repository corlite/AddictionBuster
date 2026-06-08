package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.ScreenState
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalStateRepository(context: Context) {
    private val stateFile = AtomicJsonFile(File(storageDir(context), "runtime_state.json"))

    @Synchronized
    fun load(): PersistentRuntimeState? =
        stateFile.readObjectOrNull()?.toPersistentRuntimeState()

    @Synchronized
    fun save(state: PersistentRuntimeState) {
        stateFile.writeObject(state.toJson())
    }

    @Synchronized
    fun requireState(): PersistentRuntimeState =
        load() ?: throw InvalidEnforcementContextException("missing persistent runtime state")

    fun computeOfflineGap(
        nowMillis: Long,
        currentBootMarker: String,
        confirmationThresholdMillis: Long = 30 * 60 * 1000L
    ): OfflineGap? {
        if (nowMillis < 0L) {
            throw InvalidEnforcementContextException("nowMillis must be >= 0")
        }
        val state = load() ?: return null
        if (nowMillis < state.lastEventTimeMillis) {
            throw InvalidEnforcementContextException("current time is before last event time")
        }
        val durationMillis = nowMillis - state.lastEventTimeMillis
        if (durationMillis == 0L && state.bootMarker == currentBootMarker) {
            return null
        }
        return OfflineGap(
            fromMillis = state.lastEventTimeMillis,
            toMillis = nowMillis,
            durationMillis = durationMillis,
            previousForegroundIdentityKey = state.lastForegroundIdentityKey,
            previousRawPackageName = state.lastRawPackageName,
            previousScreenState = state.lastScreenState,
            previousBootMarker = state.bootMarker,
            currentBootMarker = currentBootMarker,
            requiresUserConfirmation = state.bootMarker != currentBootMarker ||
                    durationMillis > confirmationThresholdMillis
        )
    }

    fun newBootMarker(): String = UUID.randomUUID().toString()
}

private fun PersistentRuntimeState.toJson(): JSONObject =
    JSONObject()
        .put("lastEventTimeMillis", lastEventTimeMillis)
        .put("lastForegroundIdentityKey", lastForegroundIdentityKey)
        .put("lastRawPackageName", lastRawPackageName)
        .put("lastScreenState", lastScreenState.name)
        .put("bootMarker", bootMarker)

private fun JSONObject.toPersistentRuntimeState(): PersistentRuntimeState =
    PersistentRuntimeState(
        lastEventTimeMillis = getLong("lastEventTimeMillis"),
        lastForegroundIdentityKey = optString("lastForegroundIdentityKey", ""),
        lastRawPackageName = optString("lastRawPackageName", ""),
        lastScreenState = ScreenState.valueOf(getString("lastScreenState")),
        bootMarker = getString("bootMarker")
    )
