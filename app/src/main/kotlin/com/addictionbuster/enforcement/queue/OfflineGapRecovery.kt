package com.addictionbuster.enforcement.queue

import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.ScreenState
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalStateRepository
import com.addictionbuster.enforcement.storage.OfflineGap
import com.addictionbuster.enforcement.storage.UsageCommitWriter

class OfflineGapRecovery(
    private val stateRepository: LocalStateRepository,
    private val eventStore: LocalEventStore,
    private val usageCommitWriter: UsageCommitWriter? = null
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
        if (
            gap.durationMillis > 0L &&
            gap.previousForegroundIdentityKey.isNotBlank() &&
            gap.previousScreenState == ScreenState.UNLOCKED
        ) {
            usageCommitWriter?.markOfflineGapPending(
                identityKey = gap.previousForegroundIdentityKey,
                durationMillis = gap.durationMillis
            )
            usageCommitWriter?.markPhoneOfflineGapPending(gap.durationMillis)
        }
        return gap
    }
}
