package com.addictionbuster.enforcement.stats

import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.ReasonCode
import com.addictionbuster.enforcement.ScreenState
import com.addictionbuster.enforcement.storage.EnforcementEventRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementStatsAggregatorTest {
    @Test
    fun successfulCountIgnoresDecisionRecordsAndCountsExplicitOutcomeOnce() {
        val events = listOf(
            event(
                id = "block-decision",
                type = EnforcementEventType.DECISION_RECORDED,
                reasonCode = ReasonCode.APP_SESSION_LIMIT_BLOCK
            ),
            event(
                id = "challenge-decision",
                type = EnforcementEventType.DECISION_RECORDED,
                reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED
            ),
            event(
                id = "successful-outcome-1",
                type = EnforcementEventType.INTERCEPTION_SUCCEEDED,
                reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED,
                overlaySessionId = "overlay-1"
            ),
            event(
                id = "successful-outcome-duplicate",
                type = EnforcementEventType.INTERCEPTION_SUCCEEDED,
                reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED,
                overlaySessionId = "overlay-1"
            )
        )

        assertEquals(1, events.countSuccessfulInterceptions())
    }

    private fun event(
        id: String,
        type: EnforcementEventType,
        reasonCode: ReasonCode,
        overlaySessionId: String? = null
    ): EnforcementEventRecord =
        EnforcementEventRecord(
            eventId = id,
            eventType = type,
            occurredAtMillis = 1_000L,
            foregroundIdentityKey = "com.example.reader",
            rawPackageName = "com.example.reader",
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a",
            details = buildMap {
                put("reasonCode", reasonCode.name)
                overlaySessionId?.let { put("overlaySessionId", it) }
            }
        )
}
