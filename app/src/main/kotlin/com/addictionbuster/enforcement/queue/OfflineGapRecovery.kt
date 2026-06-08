package com.addictionbuster.enforcement.queue

import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalStateRepository
import com.addictionbuster.enforcement.storage.OfflineGap

class OfflineGapRecovery(
    private val stateRepository: LocalStateRepository,
    private val eventStore: LocalEventStore
) {
    fun recover(
        nowMillis: Long,
        currentBootMarker: String
    ): OfflineGap? {
        val gap = stateRepository.computeOfflineGap(
            nowMillis = nowMillis,
            currentBootMarker = currentBootMarker
        ) ?: return null
        eventStore.append(
            eventType = EnforcementEventType.OFFLINE_GAP_DETECTED,
            occurredAtMillis = nowMillis,
            foregroundIdentityKey = gap.previousForegroundIdentityKey,
            rawPackageName = gap.previousRawPackageName,
            screenState = gap.previousScreenState,
            bootMarker = currentBootMarker,
            details = mapOf(
                "fromMillis" to gap.fromMillis.toString(),
                "toMillis" to gap.toMillis.toString(),
                "durationMillis" to gap.durationMillis.toString(),
                "previousBootMarker" to gap.previousBootMarker,
                "requiresUserConfirmation" to gap.requiresUserConfirmation.toString()
            )
        )
        return gap
    }
}
