package com.addictionbuster.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementEngineTest {
    private val engine = EnforcementEngine()

    @Test
    fun safeZoneAllowsBeforeSystemHealthFailClosed() {
        val context = context(
            app = launcherApp(),
            systemHealthState = fatalHealth(),
            safeZonePolicy = safeZoneFor(launcherApp())
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.ALLOW, decision.action)
        assertEquals(ReasonCode.SAFE_ZONE_ALLOW, decision.reasonCode)
    }

    @Test
    fun fatalSystemHealthFailClosesNonSafeApp() {
        val context = context(systemHealthState = fatalHealth())

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.FAIL_CLOSED_GLOBAL, decision.action)
        assertEquals(ReasonCode.SYSTEM_HEALTH_FAIL_CLOSED, decision.reasonCode)
    }

    @Test
    fun phoneLimitBeatsActivePass() {
        val app = normalApp()
        val context = context(
            app = app,
            usageSnapshot = usage(phoneDailyUsedMillis = 60_000L),
            ruleSnapshot = rules(
                appPolicy = appPolicy(app.identityKey, challengeEnabled = true),
                globalPolicy = globalPolicy(phoneDailyLimitMillis = 60_000L)
            ),
            activePass = ActivePass(app.identityKey, untilMillis = 120_000L)
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.SHOW_PHONE_LIMIT_BLOCK, decision.action)
        assertEquals(ReasonCode.PHONE_TOTAL_LIMIT_BLOCK, decision.reasonCode)
    }

    @Test
    fun missingAppPolicyAllowsWhenNoGlobalLimitIsExceeded() {
        val context = context(
            ruleSnapshot = rules(appPolicy = null)
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.ALLOW, decision.action)
        assertEquals(ReasonCode.MISSING_APP_POLICY_ALLOW, decision.reasonCode)
    }

    @Test
    fun phoneLimitAppliesWithoutAppPolicy() {
        val context = context(
            usageSnapshot = usage(phoneDailyUsedMillis = 60_000L),
            ruleSnapshot = rules(
                appPolicy = null,
                globalPolicy = globalPolicy(phoneDailyLimitMillis = 60_000L)
            )
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.SHOW_PHONE_LIMIT_BLOCK, decision.action)
        assertEquals(ReasonCode.PHONE_TOTAL_LIMIT_BLOCK, decision.reasonCode)
    }

    @Test
    fun overlayFailureOnSafeZoneFailsFast() {
        val app = launcherApp()
        val context = context(
            app = app,
            safeZonePolicy = safeZoneFor(app)
        )

        assertThrows(InvalidEnforcementContextException::class.java) {
            engine.overlayFailed(context, OverlayType.APP_CHALLENGE)
        }
    }

    @Test
    fun overlayFailureOnControlledAppFailClosesHome() {
        val decision = engine.overlayFailed(context(), OverlayType.APP_CHALLENGE)

        assertEquals(EnforcementAction.FAIL_CLOSED_HOME, decision.action)
        assertTrue(decision.eventsToRecord.contains("OVERLAY_FAILED"))
        assertTrue(decision.eventsToRecord.contains("FAIL_CLOSED_HOME"))
    }

    @Test
    fun pageContextMissingInsideBufferDoesNotFail() {
        val app = normalApp()
        val context = context(
            nowMillis = 1_400L,
            currentPage = null,
            pageContextMissingSinceMillis = 1_000L,
            ruleSnapshot = rules(
                appPolicy = appPolicy(app.identityKey, challengeEnabled = true),
                pagePolicy = PagePolicy(
                    identityKey = app.identityKey,
                    enabled = true,
                    activityClassNames = setOf("BlockedActivity"),
                    keywordRules = emptySet(),
                    action = PageAction.BLOCK
                )
            )
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.SHOW_APP_CHALLENGE, decision.action)
        assertTrue(decision.eventsToRecord.contains("PAGE_CONTEXT_BUFFERING"))
    }

    @Test
    fun pageContextMissingBeyondBufferFailsFast() {
        val app = normalApp()
        val context = context(
            nowMillis = 1_600L,
            currentPage = null,
            pageContextMissingSinceMillis = 1_000L,
            ruleSnapshot = rules(
                appPolicy = appPolicy(app.identityKey, challengeEnabled = true),
                pagePolicy = PagePolicy(
                    identityKey = app.identityKey,
                    enabled = true,
                    activityClassNames = setOf("BlockedActivity"),
                    keywordRules = emptySet(),
                    action = PageAction.BLOCK
                )
            )
        )

        assertThrows(InvalidEnforcementContextException::class.java) {
            engine.decide(context)
        }
    }

    @Test
    fun usageSliceDoesNotCountOverlayTime() {
        val commit = UsageSliceSettler().settle(
            previousContext = context(activeOverlayType = OverlayType.APP_CHALLENGE),
            nowMillis = 2_000L
        )

        assertEquals(1_000L, commit.sliceDurationMillis)
        assertFalse(commit.appCounted)
        assertFalse(commit.phoneCounted)
        assertEquals(0L, commit.appUsageMillis)
        assertEquals(0L, commit.phoneUsageMillis)
    }

    @Test
    fun phoneWhitelistStillCountsAppUsage() {
        val app = normalApp()
        val commit = UsageSliceSettler().settle(
            previousContext = context(
                app = app,
                ruleSnapshot = rules(
                    appPolicy = appPolicy(app.identityKey, challengeEnabled = true),
                    globalPolicy = globalPolicy(countWhitelistIdentities = setOf(app.identityKey))
                )
            ),
            nowMillis = 2_000L
        )

        assertTrue(commit.appCounted)
        assertFalse(commit.phoneCounted)
        assertEquals(1_000L, commit.appUsageMillis)
        assertEquals(0L, commit.phoneUsageMillis)
    }

    @Test
    fun phoneUsageCountsAppWithoutAppPolicy() {
        val app = normalApp()
        val commit = UsageSliceSettler().settle(
            previousContext = context(
                app = app,
                ruleSnapshot = rules(appPolicy = null)
            ),
            nowMillis = 2_000L
        )

        assertFalse(commit.appCounted)
        assertTrue(commit.phoneCounted)
        assertEquals(0L, commit.appUsageMillis)
        assertEquals(1_000L, commit.phoneUsageMillis)
    }

    @Test
    fun expiredActivePassReturnsToChallengeWhenDailyLimitHasRemainingQuota() {
        val app = normalApp()
        val context = context(
            nowMillis = 10_000L,
            app = app,
            ruleSnapshot = rules(
                appPolicy = appPolicy(
                    identityKey = app.identityKey,
                    challengeEnabled = true,
                    dailyLimitMillis = 300L * 60_000L,
                    passthroughMillis = 10L * 60_000L
                )
            ),
            usageSnapshot = usage(
                appDailyUsedMillis = 10L * 60_000L,
                appSessionUsedMillis = 10L * 60_000L
            ),
            activePass = ActivePass(app.identityKey, untilMillis = 9_000L)
        )

        val decision = engine.decide(context)

        assertEquals(EnforcementAction.SHOW_APP_CHALLENGE, decision.action)
        assertEquals(ReasonCode.APP_CHALLENGE_REQUIRED, decision.reasonCode)
    }

    @Test
    fun tickSettlesUsageAndReChallengesWhenActivePassExpired() {
        val app = normalApp()
        val rules = rules(
            appPolicy = appPolicy(
                identityKey = app.identityKey,
                challengeEnabled = true,
                dailyLimitMillis = 300L * 60_000L,
                passthroughMillis = 10L * 60_000L
            )
        )
        val previousContext = context(
            nowMillis = 1_000L,
            app = app,
            ruleSnapshot = rules,
            activePass = ActivePass(app.identityKey, untilMillis = 2_000L)
        )
        val currentContext = context(
            nowMillis = 2_500L,
            app = app,
            ruleSnapshot = rules,
            usageSnapshot = usage(appDailyUsedMillis = 1_500L),
            activePass = null
        ).copy(eventType = EnforcementEventType.TICK)

        val result = UnifiedEnforcementProcessor().process(previousContext, currentContext)

        assertEquals(1_500L, result.usageCommit.appUsageMillis)
        assertEquals(EnforcementAction.SHOW_APP_CHALLENGE, result.decision.action)
        assertEquals(ReasonCode.APP_CHALLENGE_REQUIRED, result.decision.reasonCode)
    }

    private fun context(
        nowMillis: Long = 1_000L,
        app: AppIdentity = normalApp(),
        currentPage: PageSnapshot? = PageSnapshot("MainActivity", ""),
        pageContextMissingSinceMillis: Long? = null,
        activeOverlayType: OverlayType = OverlayType.NONE,
        ruleSnapshot: RuleSnapshot = rules(appPolicy = appPolicy(app.identityKey, challengeEnabled = true)),
        usageSnapshot: UsageSnapshot = usage(),
        systemHealthState: SystemHealthState = healthy(),
        safeZonePolicy: SafeZonePolicy = safeZoneFor(app, includeApp = false),
        activePass: ActivePass? = null,
        activeCooldown: ActiveCooldown? = null
    ): EnforcementContext =
        EnforcementContext(
            nowMillis = nowMillis,
            eventType = EnforcementEventType.APP_FOREGROUND_ENTER,
            foregroundApp = app,
            previousForegroundApp = null,
            currentPage = currentPage,
            pageContextMissingSinceMillis = pageContextMissingSinceMillis,
            screenState = ScreenState.UNLOCKED,
            activeOverlayType = activeOverlayType,
            foregroundStartedAtMillis = 1_000L,
            sliceStartedAtMillis = 1_000L,
            ruleSnapshot = ruleSnapshot,
            usageSnapshot = usageSnapshot,
            systemHealthState = systemHealthState,
            safeZonePolicy = safeZonePolicy,
            activePass = activePass,
            activeCooldown = activeCooldown
        )

    private fun normalApp(): AppIdentity =
        AppIdentity(
            rawPackageName = "com.example.app",
            canonicalPackageName = "com.example.app",
            displayName = "Example",
            identityType = IdentityType.NORMAL,
            cloneGroupId = "",
            containerPackageName = "",
            userHandleKey = "",
            isSystem = false,
            isLauncher = false,
            isEmergencyAllowed = false
        )

    private fun launcherApp(): AppIdentity =
        AppIdentity(
            rawPackageName = "com.example.launcher",
            canonicalPackageName = "com.example.launcher",
            displayName = "Launcher",
            identityType = IdentityType.NORMAL,
            cloneGroupId = "",
            containerPackageName = "",
            userHandleKey = "",
            isSystem = false,
            isLauncher = true,
            isEmergencyAllowed = false
        )

    private fun safeZoneFor(app: AppIdentity, includeApp: Boolean = true): SafeZonePolicy =
        SafeZonePolicy(
            selfPackageName = "com.addictionbuster.app",
            launcherPackages = if (includeApp) setOf(app.rawPackageName) else setOf("com.example.launcher"),
            systemUiPackages = setOf("android", "com.android.systemui"),
            phonePackages = setOf("com.android.dialer"),
            inputMethodPackages = emptySet(),
            systemSettingsPackages = setOf("com.android.settings"),
            permissionSettingsPackages = setOf("com.android.settings")
        )

    private fun healthy(): SystemHealthState =
        SystemHealthState(
            accessibilityConnected = true,
            overlayPermissionGranted = true,
            notificationListenerConnected = true,
            batteryOptimizationsIgnored = true,
            foregroundServiceRunning = true
        )

    private fun fatalHealth(): SystemHealthState =
        healthy().copy(accessibilityConnected = false)

    private fun rules(
        appPolicy: AppPolicy?,
        globalPolicy: GlobalPolicy = globalPolicy(),
        pagePolicy: PagePolicy? = null,
        sleepPolicy: SleepPolicy = SleepPolicy(enabled = false, windows = emptyList()),
        clonePolicy: ClonePolicy = ClonePolicy(
            enabled = true,
            blockUnknownClones = true,
            blockKnownCloneContainers = true,
            allowManualCloneRules = false,
            knownContainerPackages = emptySet(),
            manualCloneIdentities = emptySet()
        )
    ): RuleSnapshot {
        val appPolicies = if (appPolicy == null) emptyMap() else mapOf(appPolicy.identityKey to appPolicy)
        val pagePolicies = if (pagePolicy == null) emptyMap() else mapOf(pagePolicy.identityKey to pagePolicy)
        return RuleSnapshot(
            globalPolicy = globalPolicy,
            clonePolicy = clonePolicy,
            sleepPolicy = sleepPolicy,
            appPoliciesByIdentity = appPolicies,
            pagePoliciesByIdentity = pagePolicies
        )
    }

    private fun appPolicy(
        identityKey: String,
        challengeEnabled: Boolean = false,
        dailyLimitMillis: Long = 0L,
        passthroughMillis: Long = 0L
    ): AppPolicy =
        AppPolicy(
            identityKey = identityKey,
            enabled = true,
            challengeEnabled = challengeEnabled,
            dailyLimitMillis = dailyLimitMillis,
            sessionLimitMillis = 0L,
            continuousUseLimitMillis = 0L,
            restRequiredMillis = 0L,
            dailyOpenLimit = 0,
            passthroughMillis = passthroughMillis,
            challengeWaitMillis = 0L,
            challengeRequiredTaps = 0,
            challengeHiddenCount = 0,
            challengeHiddenMillis = 0L,
            challengeConfirmText = "",
            cooldownAfterUseMillis = 0L,
            cooldownAfterQuitMillis = 0L,
            countTowardsPhoneUsage = true
        )

    private fun globalPolicy(
        phoneDailyLimitMillis: Long = 0L,
        countWhitelistIdentities: Set<String> = emptySet()
    ): GlobalPolicy =
        GlobalPolicy(
            phoneDailyLimitMillis = phoneDailyLimitMillis,
            phoneSessionLimitMillis = 0L,
            countWhitelistIdentities = countWhitelistIdentities,
            emergencyWhitelistIdentities = emptySet()
        )

    private fun usage(
        appDailyUsedMillis: Long = 0L,
        appSessionUsedMillis: Long = 0L,
        phoneDailyUsedMillis: Long = 0L
    ): UsageSnapshot =
        UsageSnapshot(
            appDailyUsedMillis = appDailyUsedMillis,
            appSessionUsedMillis = appSessionUsedMillis,
            appContinuousUsedMillis = 0L,
            appDailyOpenCount = 0,
            phoneDailyUsedMillis = phoneDailyUsedMillis,
            phoneSessionUsedMillis = 0L,
            sleepLockActive = false
        )
}
