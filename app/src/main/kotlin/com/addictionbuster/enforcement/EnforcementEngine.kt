package com.addictionbuster.enforcement

class EnforcementEngine {
    fun decide(context: EnforcementContext): EnforcementDecision {
        val app = context.foregroundApp
        val identityKey = app.identityKey
        val rules = context.ruleSnapshot
        val usage = context.usageSnapshot
        val safeZoneCategory = context.safeZonePolicy.categoryFor(app)
        val appPolicy = if (safeZoneCategory != null || app.isSystem || app.isLauncher || app.isEmergencyAllowed) {
            null
        } else {
            rules.requireAppPolicyFor(identityKey)
        }
        val pageEvaluation = if (safeZoneCategory != null || app.isSystem || app.isLauncher || app.isEmergencyAllowed) {
            PageEvaluation(decision = null, eventsToRecord = emptyList())
        } else {
            pageEvaluation(context, app)
        }

        return when {
            safeZoneCategory != null -> decision(
                EnforcementAction.ALLOW,
                Priority.SAFE_ZONE_ALLOW,
                app,
                ReasonCode.SAFE_ZONE_ALLOW,
                "safe zone identity is always allowed: $safeZoneCategory",
                OverlayType.NONE
            )

            app.isSystem || app.isLauncher -> decision(
                EnforcementAction.ALLOW,
                Priority.SYSTEM_ALLOW,
                app,
                ReasonCode.SELF_OR_SYSTEM_ALLOW,
                "system or launcher identity is always allowed",
                OverlayType.NONE
            )

            app.isEmergencyAllowed || identityKey in rules.globalPolicy.emergencyWhitelistIdentities -> decision(
                EnforcementAction.ALLOW,
                Priority.EMERGENCY_ALLOW,
                app,
                ReasonCode.EMERGENCY_ALLOW,
                "emergency identity is allowed",
                OverlayType.NONE
            )

            context.systemHealthState.hasFatalIssue -> decision(
                EnforcementAction.FAIL_CLOSED_GLOBAL,
                Priority.SYSTEM_HEALTH_FAIL_CLOSED,
                app,
                ReasonCode.SYSTEM_HEALTH_FAIL_CLOSED,
                "system health fatal issues: ${context.systemHealthState.fatalIssues.joinToString()}",
                OverlayType.NONE,
                "SYSTEM_HEALTH_FAIL_CLOSED"
            )

            rules.clonePolicy.shouldBlock(app) -> decision(
                EnforcementAction.SHOW_CLONE_BLOCK,
                Priority.CLONE_BLOCK,
                app,
                ReasonCode.CLONE_POLICY_BLOCK,
                "clone policy blocks this identity",
                OverlayType.CLONE_BLOCK,
                "CLONE_BLOCKED"
            )

            rules.sleepPolicy.enabled && usage.sleepLockActive -> decision(
                EnforcementAction.SHOW_SLEEP_LOCK,
                Priority.SLEEP_LOCK,
                app,
                ReasonCode.SLEEP_LOCK_BLOCK,
                "sleep lock is active",
                OverlayType.SLEEP_LOCK,
                "SLEEP_LOCK_REACHED"
            )

            phoneLimitApplies(context) &&
                    exceeded(usage.phoneDailyUsedMillis, rules.globalPolicy.phoneDailyLimitMillis) -> decision(
                EnforcementAction.SHOW_PHONE_LIMIT_BLOCK,
                Priority.PHONE_DAILY,
                app,
                ReasonCode.PHONE_TOTAL_LIMIT_BLOCK,
                "phone daily limit reached",
                OverlayType.PHONE_LIMIT_BLOCK,
                "PHONE_LIMIT_REACHED"
            )

            phoneLimitApplies(context) &&
                    exceeded(usage.phoneSessionUsedMillis, rules.globalPolicy.phoneSessionLimitMillis) -> decision(
                EnforcementAction.SHOW_PHONE_LIMIT_BLOCK,
                Priority.PHONE_SESSION,
                app,
                ReasonCode.PHONE_SESSION_LIMIT_BLOCK,
                "phone session limit reached",
                OverlayType.PHONE_LIMIT_BLOCK,
                "PHONE_LIMIT_REACHED"
            )

            appPolicy != null && !appPolicy.enabled -> decision(
                EnforcementAction.ALLOW,
                Priority.ALLOW,
                app,
                ReasonCode.POLICY_DISABLED_ALLOW,
                "app policy is disabled",
                OverlayType.NONE,
                *pageEvaluation.eventsToRecord.toTypedArray()
            )

            appPolicy != null && exceeded(usage.appDailyUsedMillis, appPolicy.dailyLimitMillis) -> appLimit(
                app,
                ReasonCode.APP_DAILY_LIMIT_BLOCK,
                "app daily limit reached"
            )

            appPolicy != null && exceeded(usage.appSessionUsedMillis, appPolicy.sessionLimitMillis) -> appLimit(
                app,
                ReasonCode.APP_SESSION_LIMIT_BLOCK,
                "app session limit reached"
            )

            appPolicy != null && exceeded(usage.appContinuousUsedMillis, appPolicy.continuousUseLimitMillis) -> appLimit(
                app,
                ReasonCode.APP_CONTINUOUS_USE_BLOCK,
                "app continuous-use limit reached"
            )

            appPolicy != null && appPolicy.dailyOpenLimit > 0 && usage.appDailyOpenCount >= appPolicy.dailyOpenLimit -> appLimit(
                app,
                ReasonCode.APP_OPEN_COUNT_BLOCK,
                "app open-count limit reached"
            )

            pageEvaluation.decision != null -> pageEvaluation.decision

            context.activeCooldown?.matches(app, context.nowMillis) == true -> decision(
                EnforcementAction.SHOW_COOLDOWN_BLOCK,
                Priority.COOLDOWN,
                app,
                ReasonCode.APP_COOLDOWN_BLOCK,
                "app cooldown is active",
                OverlayType.COOLDOWN_BLOCK,
                *mergeEvents(pageEvaluation.eventsToRecord, "COOLDOWN_STARTED")
            )

            context.activePass?.matches(app, context.nowMillis) == true -> decision(
                EnforcementAction.ALLOW,
                Priority.ALLOW,
                app,
                ReasonCode.ACTIVE_PASS_ALLOW,
                "active pass allows this identity",
                OverlayType.NONE,
                *pageEvaluation.eventsToRecord.toTypedArray()
            )

            appPolicy != null && appPolicy.challengeEnabled -> decision(
                EnforcementAction.SHOW_APP_CHALLENGE,
                Priority.CHALLENGE,
                app,
                ReasonCode.APP_CHALLENGE_REQUIRED,
                "app challenge is required",
                OverlayType.APP_CHALLENGE,
                *mergeEvents(pageEvaluation.eventsToRecord, "CHALLENGE_SHOWN")
            )

            else -> throw InvalidEnforcementContextException(
                "enabled policy has no blocking rule; this should have been rejected by AppPolicy validation"
            )
        }
    }

    fun overlayFailed(
        context: EnforcementContext,
        failedOverlayType: OverlayType
    ): EnforcementDecision {
        if (failedOverlayType == OverlayType.NONE) {
            throw InvalidEnforcementContextException("failedOverlayType must be a blocking overlay")
        }
        val safeZoneCategory = context.safeZonePolicy.categoryFor(context.foregroundApp)
        if (safeZoneCategory != null) {
            throw InvalidEnforcementContextException(
                "overlay failure was reported for safe zone identity: $safeZoneCategory"
            )
        }
        return EnforcementDecision(
            action = EnforcementAction.FAIL_CLOSED_HOME,
            priority = Priority.SYSTEM_ALLOW,
            targetIdentity = context.foregroundApp,
            reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED,
            reasonText = "overlay creation failed; fail closed to home",
            durationMillis = 0L,
            overlayType = failedOverlayType,
            eventsToRecord = listOf("OVERLAY_FAILED", "FAIL_CLOSED_HOME")
        )
    }

    private fun phoneLimitApplies(context: EnforcementContext): Boolean {
        val app = context.foregroundApp
        if (app.isSystem || app.isLauncher || app.isEmergencyAllowed) return false
        if (app.identityKey in context.ruleSnapshot.globalPolicy.countWhitelistIdentities) return false
        return true
    }

    private fun pageEvaluation(
        context: EnforcementContext,
        app: AppIdentity
    ): PageEvaluation {
        val pagePolicy = context.ruleSnapshot.pagePolicyFor(app.identityKey)
            ?: return PageEvaluation(decision = null, eventsToRecord = emptyList())
        if (!pagePolicy.matches(app.identityKey, context.currentPage)) {
            return PageEvaluation(decision = null, eventsToRecord = emptyList())
        }
        if (pagePolicy.action == PageAction.RECORD_ONLY) {
            return PageEvaluation(
                decision = null,
                eventsToRecord = listOf("PAGE_POLICY_RECORDED")
            )
        }
        return PageEvaluation(decision = decision(
            action = if (pagePolicy.action == PageAction.CHALLENGE) {
                EnforcementAction.SHOW_APP_CHALLENGE
            } else {
                EnforcementAction.SHOW_PAGE_BLOCK
            },
            priority = Priority.PAGE,
            target = app,
            reasonCode = ReasonCode.PAGE_KEYWORD_BLOCK,
            reasonText = "page policy matched",
            overlayType = if (pagePolicy.action == PageAction.CHALLENGE) {
                OverlayType.APP_CHALLENGE
            } else {
                OverlayType.PAGE_BLOCK
            },
            events = arrayOf("PAGE_BLOCK_REACHED")
        ), eventsToRecord = emptyList())
    }

    private fun appLimit(
        app: AppIdentity,
        reasonCode: ReasonCode,
        reasonText: String
    ): EnforcementDecision = decision(
        EnforcementAction.SHOW_APP_LIMIT_BLOCK,
        priorityForAppLimit(reasonCode),
        app,
        reasonCode,
        reasonText,
        OverlayType.APP_LIMIT_BLOCK,
        "APP_LIMIT_REACHED"
    )

    private fun priorityForAppLimit(reasonCode: ReasonCode): Int = when (reasonCode) {
        ReasonCode.APP_DAILY_LIMIT_BLOCK -> Priority.APP_DAILY
        ReasonCode.APP_SESSION_LIMIT_BLOCK -> Priority.APP_SESSION
        ReasonCode.APP_CONTINUOUS_USE_BLOCK -> Priority.APP_CONTINUOUS
        ReasonCode.APP_OPEN_COUNT_BLOCK -> Priority.APP_OPEN_COUNT
        else -> throw InvalidEnforcementContextException("unsupported app limit reason: $reasonCode")
    }

    private fun exceeded(usedMillis: Long, limitMillis: Long): Boolean =
        limitMillis > 0L && usedMillis >= limitMillis

    private fun mergeEvents(existingEvents: List<String>, vararg newEvents: String): Array<String> =
        (existingEvents + newEvents).toTypedArray()

    private fun decision(
        action: EnforcementAction,
        priority: Int,
        target: AppIdentity,
        reasonCode: ReasonCode,
        reasonText: String,
        overlayType: OverlayType,
        vararg events: String
    ): EnforcementDecision = EnforcementDecision(
        action = action,
        priority = priority,
        targetIdentity = target,
        reasonCode = reasonCode,
        reasonText = reasonText,
        durationMillis = 0L,
        overlayType = overlayType,
        eventsToRecord = events.toList()
    )

    private object Priority {
        const val SAFE_ZONE_ALLOW = 0
        const val SYSTEM_ALLOW = 1
        const val EMERGENCY_ALLOW = 2
        const val SYSTEM_HEALTH_FAIL_CLOSED = 3
        const val CLONE_BLOCK = 4
        const val SLEEP_LOCK = 5
        const val PHONE_DAILY = 6
        const val PHONE_SESSION = 7
        const val APP_DAILY = 8
        const val APP_SESSION = 9
        const val APP_CONTINUOUS = 10
        const val APP_OPEN_COUNT = 11
        const val PAGE = 12
        const val COOLDOWN = 13
        const val CHALLENGE = 14
        const val ALLOW = 15
    }

    private data class PageEvaluation(
        val decision: EnforcementDecision?,
        val eventsToRecord: List<String>
    )
}
