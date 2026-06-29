package com.addictionbuster.enforcement.stats

import com.addictionbuster.enforcement.AppIdentity
import com.addictionbuster.enforcement.EnforcementAction
import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.OverlayType
import com.addictionbuster.enforcement.ReasonCode

data class SuccessfulInterceptionOutcome(
    val overlaySessionId: String,
    val targetIdentity: AppIdentity,
    val triggerAction: EnforcementAction,
    val triggerReasonCode: ReasonCode,
    val overlayType: OverlayType
) {
    init {
        if (overlaySessionId.isBlank()) {
            throw InvalidEnforcementContextException("overlaySessionId is required")
        }
        if (!SuccessfulInterceptionPolicy.isAppScopedDecision(triggerAction, triggerReasonCode)) {
            throw InvalidEnforcementContextException("successful interception must come from an app-scoped block")
        }
    }
}

object SuccessfulInterceptionPolicy {
    private val appScopedDecisionPairs = setOf(
        EnforcementAction.SHOW_APP_CHALLENGE to ReasonCode.APP_CHALLENGE_REQUIRED,
        EnforcementAction.SHOW_APP_CHALLENGE to ReasonCode.PAGE_KEYWORD_BLOCK,
        EnforcementAction.SHOW_APP_LIMIT_BLOCK to ReasonCode.APP_DAILY_LIMIT_BLOCK,
        EnforcementAction.SHOW_APP_LIMIT_BLOCK to ReasonCode.APP_SESSION_LIMIT_BLOCK,
        EnforcementAction.SHOW_APP_LIMIT_BLOCK to ReasonCode.APP_CONTINUOUS_USE_BLOCK,
        EnforcementAction.SHOW_APP_LIMIT_BLOCK to ReasonCode.APP_OPEN_COUNT_BLOCK,
        EnforcementAction.SHOW_COOLDOWN_BLOCK to ReasonCode.APP_COOLDOWN_BLOCK,
        EnforcementAction.SHOW_PAGE_BLOCK to ReasonCode.PAGE_KEYWORD_BLOCK
    )

    fun isEligible(context: EnforcementContext, decision: EnforcementDecision): Boolean {
        val appPolicyEnabled = context.ruleSnapshot
            .appPolicyFor(decision.targetIdentity.identityKey)
            ?.enabled == true
        return isEligible(appPolicyEnabled, decision.action, decision.reasonCode)
    }

    internal fun isEligible(
        appPolicyEnabled: Boolean,
        action: EnforcementAction,
        reasonCode: ReasonCode
    ): Boolean = appPolicyEnabled && isAppScopedDecision(action, reasonCode)

    internal fun shouldRecordOutcome(
        overlaySessionEligible: Boolean,
        homeSent: Boolean
    ): Boolean = overlaySessionEligible && homeSent

    internal fun isAppScopedDecision(action: EnforcementAction, reasonCode: ReasonCode): Boolean =
        action to reasonCode in appScopedDecisionPairs
}
