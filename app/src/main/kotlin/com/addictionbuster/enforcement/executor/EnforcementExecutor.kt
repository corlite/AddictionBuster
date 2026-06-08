package com.addictionbuster.enforcement.executor

import android.content.Context
import com.addictionbuster.enforcement.EnforcementAction
import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.OverlayType
import com.addictionbuster.enforcement.SystemHealthIssue
import com.addictionbuster.enforcement.health.SystemHealthNotifier

class EnforcementExecutor(
    private val context: Context,
    private val overlayController: OverlayController,
    private val homeActionPerformer: HomeActionPerformer,
    private val overlayPermissionChecker: OverlayPermissionChecker = AndroidOverlayPermissionChecker(context),
    private val failClosedHomeLimiter: FailClosedHomeLimiter = FailClosedHomeLimiter(),
    private val notifier: SystemHealthNotifier = SystemHealthNotifier(context)
) {
    fun execute(
        decision: EnforcementDecision,
        enforcementContext: EnforcementContext
    ): EnforcementExecutionResult {
        if (enforcementContext.safeZonePolicy.isSafe(decision.targetIdentity) &&
            (decision.action == EnforcementAction.FAIL_CLOSED_HOME ||
                    decision.action == EnforcementAction.FAIL_CLOSED_GLOBAL)
        ) {
            throw InvalidEnforcementContextException("safe zone identity must not be fail-closed")
        }

        return when (decision.action) {
            EnforcementAction.ALLOW,
            EnforcementAction.NO_OP -> EnforcementExecutionResult.NoAction(decision)

            EnforcementAction.SHOW_APP_CHALLENGE,
            EnforcementAction.SHOW_APP_LIMIT_BLOCK,
            EnforcementAction.SHOW_PHONE_LIMIT_BLOCK,
            EnforcementAction.SHOW_SLEEP_LOCK,
            EnforcementAction.SHOW_PAGE_BLOCK,
            EnforcementAction.SHOW_CLONE_BLOCK,
            EnforcementAction.SHOW_COOLDOWN_BLOCK -> showOverlayOrFailClosed(
                decision = decision,
                context = enforcementContext
            )

            EnforcementAction.GO_HOME,
            EnforcementAction.FAIL_CLOSED_HOME,
            EnforcementAction.FAIL_CLOSED_GLOBAL -> performLimitedHome(decision, enforcementContext)
        }
    }

    private fun showOverlayOrFailClosed(
        decision: EnforcementDecision,
        context: EnforcementContext
    ): EnforcementExecutionResult {
        if (!overlayPermissionChecker.canShowOverlay()) {
            notifier.notifyFatalIssues(setOf(SystemHealthIssue.OVERLAY_PERMISSION_MISSING))
            return performLimitedHome(
                decision = decision.copyForFailClosedHome("overlay permission missing before show"),
                context = context
            )
        }
        val shown = overlayController.show(decision, context)
        if (!shown) {
            notifier.notifyFatalIssues(setOf(SystemHealthIssue.OVERLAY_PERMISSION_MISSING))
            return performLimitedHome(
                decision = decision.copyForFailClosedHome("overlay controller failed to show"),
                context = context
            )
        }
        return EnforcementExecutionResult.OverlayShown(decision)
    }

    private fun performLimitedHome(
        decision: EnforcementDecision,
        context: EnforcementContext
    ): EnforcementExecutionResult {
        val targetKey = decision.targetIdentity.identityKey
        val nowMillis = context.nowMillis
        if (!failClosedHomeLimiter.canAttempt(targetKey, nowMillis)) {
            notifier.notifyFatalIssues(context.systemHealthState.fatalIssues)
            return EnforcementExecutionResult.HomeSuppressed(
                decision = decision,
                reason = "home attempt limit reached"
            )
        }
        failClosedHomeLimiter.recordAttempt(targetKey, nowMillis)
        val sent = homeActionPerformer.performHome()
        return if (sent) {
            EnforcementExecutionResult.HomeSent(decision)
        } else {
            notifier.notifyFatalIssues(context.systemHealthState.fatalIssues)
            EnforcementExecutionResult.HomeFailed(decision)
        }
    }

    private fun EnforcementDecision.copyForFailClosedHome(reason: String): EnforcementDecision =
        copy(
            action = EnforcementAction.FAIL_CLOSED_HOME,
            reasonText = reason,
            overlayType = OverlayType.NONE,
            eventsToRecord = eventsToRecord + listOf("OVERLAY_FAILED", "FAIL_CLOSED_HOME")
        )
}

sealed class EnforcementExecutionResult {
    abstract val decision: EnforcementDecision

    data class NoAction(override val decision: EnforcementDecision) : EnforcementExecutionResult()
    data class OverlayShown(override val decision: EnforcementDecision) : EnforcementExecutionResult()
    data class HomeSent(override val decision: EnforcementDecision) : EnforcementExecutionResult()
    data class HomeFailed(override val decision: EnforcementDecision) : EnforcementExecutionResult()
    data class HomeSuppressed(
        override val decision: EnforcementDecision,
        val reason: String
    ) : EnforcementExecutionResult()
}

interface OverlayController {
    fun show(decision: EnforcementDecision, context: EnforcementContext): Boolean
    fun removeAll()
}

interface HomeActionPerformer {
    fun performHome(): Boolean
}

interface OverlayPermissionChecker {
    fun canShowOverlay(): Boolean
}
