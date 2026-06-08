package com.addictionbuster.enforcement

class UnifiedEnforcementProcessor(
    private val usageSliceSettler: UsageSliceSettler = UsageSliceSettler(),
    private val enforcementEngine: EnforcementEngine = EnforcementEngine()
) {
    fun process(
        previousContext: EnforcementContext,
        currentContext: EnforcementContext
    ): EnforcementProcessResult {
        if (currentContext.nowMillis < previousContext.nowMillis) {
            throw InvalidEnforcementContextException("currentContext is older than previousContext")
        }
        val usageCommit = usageSliceSettler.settle(previousContext, currentContext.nowMillis)
        val decision = enforcementEngine.decide(currentContext)
        return EnforcementProcessResult(
            usageCommit = usageCommit,
            decision = decision
        )
    }

    fun overlayFailed(
        context: EnforcementContext,
        failedOverlayType: OverlayType
    ): EnforcementDecision = enforcementEngine.overlayFailed(context, failedOverlayType)
}

data class EnforcementProcessResult(
    val usageCommit: UsageCommit,
    val decision: EnforcementDecision
)
