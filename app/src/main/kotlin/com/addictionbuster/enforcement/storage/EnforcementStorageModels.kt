package com.addictionbuster.enforcement.storage

import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.ScreenState

data class EnforcementEventRecord(
    val eventId: String,
    val eventType: EnforcementEventType,
    val occurredAtMillis: Long,
    val foregroundIdentityKey: String,
    val rawPackageName: String,
    val screenState: ScreenState,
    val bootMarker: String,
    val details: Map<String, String>
) {
    init {
        requireText(eventId, "eventId")
        requireNonNegative(occurredAtMillis, "occurredAtMillis")
        requireText(bootMarker, "bootMarker")
    }
}

data class PersistentRuntimeState(
    val lastEventTimeMillis: Long,
    val lastForegroundIdentityKey: String,
    val lastRawPackageName: String,
    val lastScreenState: ScreenState,
    val bootMarker: String
) {
    init {
        requireNonNegative(lastEventTimeMillis, "lastEventTimeMillis")
        requireText(bootMarker, "bootMarker")
    }
}

data class OfflineGap(
    val fromMillis: Long,
    val toMillis: Long,
    val durationMillis: Long,
    val previousForegroundIdentityKey: String,
    val previousRawPackageName: String,
    val previousScreenState: ScreenState,
    val previousBootMarker: String,
    val currentBootMarker: String,
    val requiresUserConfirmation: Boolean
) {
    init {
        requireNonNegative(fromMillis, "fromMillis")
        requireNonNegative(toMillis, "toMillis")
        requireNonNegative(durationMillis, "durationMillis")
        if (toMillis < fromMillis) {
            throw InvalidEnforcementContextException("offline gap toMillis is before fromMillis")
        }
    }
}

private fun requireText(value: String, field: String) {
    if (value.isBlank()) {
        throw InvalidEnforcementContextException("$field is required")
    }
}

private fun requireNonNegative(value: Long, field: String) {
    if (value < 0L) {
        throw InvalidEnforcementContextException("$field must be >= 0")
    }
}
