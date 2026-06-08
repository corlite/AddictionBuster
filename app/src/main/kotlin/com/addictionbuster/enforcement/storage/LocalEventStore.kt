package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.ScreenState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalEventStore(context: Context) {
    private val storeFile = AtomicJsonFile(File(storageDir(context), "events.json"))

    @Synchronized
    fun append(record: EnforcementEventRecord) {
        val records = readAll().toMutableList()
        records.add(record)
        storeFile.writeObject(JSONObject().put("events", records.toJsonArray()))
    }

    @Synchronized
    fun append(
        eventType: EnforcementEventType,
        occurredAtMillis: Long,
        foregroundIdentityKey: String,
        rawPackageName: String,
        screenState: ScreenState,
        bootMarker: String,
        details: Map<String, String> = emptyMap()
    ): EnforcementEventRecord {
        val record = EnforcementEventRecord(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            occurredAtMillis = occurredAtMillis,
            foregroundIdentityKey = foregroundIdentityKey,
            rawPackageName = rawPackageName,
            screenState = screenState,
            bootMarker = bootMarker,
            details = details
        )
        append(record)
        return record
    }

    @Synchronized
    fun readAll(): List<EnforcementEventRecord> {
        val root = storeFile.readObjectOrNull() ?: return emptyList()
        val events = root.optJSONArray("events")
            ?: throw InvalidEnforcementContextException("EventStore file missing events array")
        return buildList {
            for (index in 0 until events.length()) {
                add(events.getJSONObject(index).toEventRecord())
            }
        }
    }

    @Synchronized
    fun lastRecord(): EnforcementEventRecord? =
        readAll().maxByOrNull { it.occurredAtMillis }

    private fun List<EnforcementEventRecord>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { array.put(it.toJson()) }
        return array
    }
}

private fun EnforcementEventRecord.toJson(): JSONObject =
    JSONObject()
        .put("eventId", eventId)
        .put("eventType", eventType.name)
        .put("occurredAtMillis", occurredAtMillis)
        .put("foregroundIdentityKey", foregroundIdentityKey)
        .put("rawPackageName", rawPackageName)
        .put("screenState", screenState.name)
        .put("bootMarker", bootMarker)
        .put("details", JSONObject(details))

private fun JSONObject.toEventRecord(): EnforcementEventRecord =
    EnforcementEventRecord(
        eventId = getString("eventId"),
        eventType = EnforcementEventType.valueOf(getString("eventType")),
        occurredAtMillis = getLong("occurredAtMillis"),
        foregroundIdentityKey = optString("foregroundIdentityKey", ""),
        rawPackageName = optString("rawPackageName", ""),
        screenState = ScreenState.valueOf(getString("screenState")),
        bootMarker = getString("bootMarker"),
        details = getJSONObject("details").toStringMap()
    )

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> getString(key) }

internal fun storageDir(context: Context): File {
    val directory = File(context.applicationContext.filesDir, "enforcement_v2")
    if (!directory.exists() && !directory.mkdirs()) {
        throw InvalidEnforcementContextException("unable to create enforcement storage directory")
    }
    return directory
}
