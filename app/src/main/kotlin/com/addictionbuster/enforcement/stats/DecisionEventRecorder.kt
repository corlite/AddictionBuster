package com.addictionbuster.enforcement.stats

import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.storage.LocalEventStore

class DecisionEventRecorder(
    private val eventStore: LocalEventStore
) {
    fun record(context: EnforcementContext, decision: EnforcementDecision, bootMarker: String) {
        eventStore.append(
            eventType = EnforcementEventType.DECISION_RECORDED,
            occurredAtMillis = context.nowMillis,
            foregroundIdentityKey = context.foregroundApp.identityKey,
            rawPackageName = context.foregroundApp.rawPackageName,
            screenState = context.screenState,
            bootMarker = bootMarker,
            details = mapOf(
                "action" to decision.action.name,
                "reasonCode" to decision.reasonCode.name,
                "overlayType" to decision.overlayType.name,
                "priority" to decision.priority.toString(),
                "systemHealthIssues" to context.systemHealthState.fatalIssues.joinToString(",") { it.name },
                "accessibilityConnected" to context.systemHealthState.accessibilityConnected.toString(),
                "overlayPermissionGranted" to context.systemHealthState.overlayPermissionGranted.toString(),
                "foregroundServiceRunning" to context.systemHealthState.foregroundServiceRunning.toString()
            )
        )
    }
}
