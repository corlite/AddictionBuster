package com.addictionbuster.enforcement.runtime

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.addictionbuster.BusterAccessibilityService
import com.addictionbuster.V2DiagnosticBridge
import com.addictionbuster.MascotSoundPlayer
import com.addictionbuster.V2EnforcementForegroundService
import com.addictionbuster.bootstrap.V2RequiredSetupActivity
import com.addictionbuster.enforcement.AndroidSafeZonePolicyFactory
import com.addictionbuster.enforcement.ActivePass
import com.addictionbuster.enforcement.AppIdentity
import com.addictionbuster.enforcement.EnforcementAction
import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.OverlayType
import com.addictionbuster.enforcement.RuleSnapshot
import com.addictionbuster.enforcement.ScreenState
import com.addictionbuster.enforcement.UsageSnapshot
import com.addictionbuster.enforcement.executor.AccessibilityHomeActionPerformer
import com.addictionbuster.enforcement.executor.EnforcementExecutor
import com.addictionbuster.enforcement.executor.EnforcementExecutionResult
import com.addictionbuster.enforcement.executor.OverlayActionHandler
import com.addictionbuster.enforcement.executor.OverlayPermissionChecker
import com.addictionbuster.enforcement.executor.SimpleOverlayController
import com.addictionbuster.enforcement.health.AndroidSystemHealthReader
import com.addictionbuster.enforcement.identity.AndroidAppIdentityResolver
import com.addictionbuster.enforcement.page.AccessibilityPageSnapshotExtractor
import com.addictionbuster.enforcement.page.PageContextTracker
import com.addictionbuster.enforcement.queue.OfflineGapRecovery
import com.addictionbuster.enforcement.queue.SingleThreadEnforcementQueue
import com.addictionbuster.enforcement.sleep.SleepScheduleEvaluator
import com.addictionbuster.enforcement.stats.DecisionEventRecorder
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPassRepository
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository
import com.addictionbuster.enforcement.storage.LocalRuleRepository
import com.addictionbuster.enforcement.storage.LocalSetupStateRepository
import com.addictionbuster.enforcement.storage.LocalStateRepository
import com.addictionbuster.enforcement.storage.PersistentRuntimeState
import com.addictionbuster.enforcement.storage.UsageCommitWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object V2AccessibilityRuntime {
    private const val TAG = "V2AccessibilityRuntime"
    private const val TICK_INTERVAL_MILLIS = 1_000L
    private const val WINDOW_EVENT_TYPES =
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runtime: RuntimeHolder? = null

    @JvmStatic
    fun onServiceConnected(service: BusterAccessibilityService) {
        runtime?.stop()
        val holder = RuntimeHolder.create(service)
        holder.queue.start()
        holder.recoverOfflineGap()
        runtime = holder
        scope.launch { holder.runTicks() }
        V2DiagnosticBridge.log(service, "v2", "runtime connected")
        Log.d(TAG, "v2 accessibility runtime connected")
    }

    @JvmStatic
    fun onAccessibilityEvent(service: BusterAccessibilityService, event: AccessibilityEvent?) {
        val holder = runtime ?: RuntimeHolder.create(service).also {
            it.queue.start()
            runtime = it
            scope.launch { it.runTicks() }
        }
        if (event == null || (event.eventType and WINDOW_EVENT_TYPES) == 0) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        holder.enqueueWindowEvent(event, packageName)
    }

    @JvmStatic
    fun onScreenEvent(service: BusterAccessibilityService, action: String?) {
        val holder = runtime ?: RuntimeHolder.create(service).also {
            it.queue.start()
            runtime = it
            scope.launch { it.runTicks() }
        }
        holder.enqueueScreenEvent(action.orEmpty())
    }

    @JvmStatic
    fun onDestroy() {
        runtime?.stop()
        runtime = null
        Log.d(TAG, "v2 accessibility runtime destroyed")
    }

    private class RuntimeHolder(
        private val service: BusterAccessibilityService,
        private val ruleRepository: LocalRuleRepository,
        private val appUsageRepository: LocalAppUsageRepository,
        private val phoneUsageRepository: LocalPhoneUsageRepository,
        private val passRepository: LocalPassRepository,
        private val setupStateRepository: LocalSetupStateRepository,
        private val stateRepository: LocalStateRepository,
        private val eventStore: LocalEventStore,
        private val identityResolver: AndroidAppIdentityResolver,
        private val pageExtractor: AccessibilityPageSnapshotExtractor,
        private val pageContextTracker: PageContextTracker,
        private val healthReader: AndroidSystemHealthReader,
        private val sleepScheduleEvaluator: SleepScheduleEvaluator,
        val queue: SingleThreadEnforcementQueue,
        val executor: RuntimeExecutor,
        private val decisionEventRecorder: DecisionEventRecorder,
        private val bootMarker: String,
        private val overlayState: RuntimeOverlayState
    ) {
        private var lastContext: EnforcementContext? = null
        @Volatile
        private var active = true
        private val ingressLock = Any()
        private val homeActionPerformer = AccessibilityHomeActionPerformer(service)
        private val eventProcessor = OrderedRuntimeEventProcessor<RuntimeInput>(
            scope = V2AccessibilityRuntime.scope,
            handler = ::processInput,
            failureHandler = ::logInputFailure
        )

        fun enqueueWindowEvent(event: AccessibilityEvent, packageName: String) {
            lateinit var eventCopy: AccessibilityEvent
            val accepted = synchronized(ingressLock) {
                eventCopy = copyAccessibilityEvent(event)
                eventProcessor.submit(
                    RuntimeInput.Window(
                        event = eventCopy,
                        eventType = event.eventType,
                        packageName = packageName,
                        receivedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            if (!accepted) {
                recycleAccessibilityEvent(eventCopy)
            }
        }

        fun enqueueScreenEvent(action: String) {
            synchronized(ingressLock) {
                eventProcessor.submit(RuntimeInput.Screen(action, System.currentTimeMillis()))
            }
        }

        fun enqueueOverlayAction(action: RuntimeOverlayAction) {
            synchronized(ingressLock) {
                when (action) {
                    is RuntimeOverlayAction.Primary -> {
                        overlayState.challengeTransitionPendingIdentityKey =
                            action.decision.targetIdentity.identityKey
                    }
                    is RuntimeOverlayAction.Quit -> {
                        val homeSent = homeActionPerformer.performHome()
                        overlayState.quitPendingIdentityKey =
                            action.decision.targetIdentity.identityKey.takeIf { homeSent }
                        if (!homeSent) {
                            overlayState.activeOverlayType = OverlayType.NONE
                        }
                    }
                }
                eventProcessor.submit(RuntimeInput.Overlay(action, System.currentTimeMillis()))
            }
        }

        private fun enqueueTick() {
            synchronized(ingressLock) {
                eventProcessor.submit(RuntimeInput.Tick(System.currentTimeMillis()))
            }
        }

        fun stop() {
            active = false
            eventProcessor.close()
            executor.removeOverlays()
            overlayState.activeOverlayType = OverlayType.NONE
            overlayState.quitPendingIdentityKey = null
            overlayState.challengeTransitionPendingIdentityKey = null
        }

        private suspend fun processInput(input: RuntimeInput) {
            if (!active) {
                discardInput(input)
                return
            }
            when (input) {
                is RuntimeInput.Window -> try {
                    processEvent(input.event, input.packageName, input.receivedAtMillis)
                } finally {
                    recycleAccessibilityEvent(input.event)
                }
                is RuntimeInput.Screen -> processScreenEvent(input.action, input.receivedAtMillis)
                is RuntimeInput.Tick -> processTick(input.receivedAtMillis)
                is RuntimeInput.Overlay -> processOverlayAction(input.action, input.receivedAtMillis)
            }
        }

        private fun logInputFailure(input: RuntimeInput, throwable: Throwable) {
            when (input) {
                is RuntimeInput.Window -> {
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "runtime exception package=${input.packageName} type=${input.eventType} error=${throwable.message}"
                    )
                    Log.w(TAG, "failed to process v2 accessibility event package=${input.packageName}", throwable)
                }
                is RuntimeInput.Screen -> {
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "screen event exception action=${input.action} error=${throwable.message}"
                    )
                    Log.w(TAG, "failed to process v2 screen event action=${input.action}", throwable)
                }
                is RuntimeInput.Tick -> {
                    V2DiagnosticBridge.log(service, "v2", "tick exception error=${throwable.message}")
                    Log.w(TAG, "failed to process v2 tick", throwable)
                }
                is RuntimeInput.Overlay -> {
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "overlay action exception action=${input.action.javaClass.simpleName} error=${throwable.message}"
                    )
                    Log.w(TAG, "failed to process v2 overlay action", throwable)
                }
            }
        }

        private fun discardInput(input: RuntimeInput) {
            if (input is RuntimeInput.Window) {
                recycleAccessibilityEvent(input.event)
            }
        }

        fun recoverOfflineGap() {
            OfflineGapRecovery(
                stateRepository = stateRepository,
                eventStore = eventStore,
                usageCommitWriter = UsageCommitWriter(appUsageRepository, phoneUsageRepository)
            ).recover(
                nowMillis = System.currentTimeMillis(),
                currentBootMarker = bootMarker
            )
        }

        private suspend fun processEvent(
            event: AccessibilityEvent,
            packageName: String,
            nowMillis: Long
        ) {
            if (isOwnOverlayWindowEvent(event, packageName)) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "ignored own overlay window event type=${event.eventType} class=${event.className ?: ""} activeOverlay=${overlayState.activeOverlayType}"
                )
                return
            }
            val identity = identityResolver.resolve(packageName)
            val quitPendingIdentity = overlayState.quitPendingIdentityKey
            if (quitPendingIdentity == identity.identityKey) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "suppress challenge while quit-home pending package=$packageName"
                )
                homeActionPerformer.performHome()
            }
            val clearQuitPendingAfterCommit =
                quitPendingIdentity != null && quitPendingIdentity != identity.identityKey
            V2DiagnosticBridge.log(
                service,
                "v2",
                "event type=${event.eventType} package=$packageName class=${event.className ?: ""} identity=${identity.identityKey}"
            )
            val rules = try {
                ruleRepository.load()
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "rules unavailable package=$packageName identity=${identity.identityKey} error=${throwable.message}"
                )
                handleUnavailableRules(identity, nowMillis)
                return
            }
            V2DiagnosticBridge.log(
                service,
                "v2",
                "rules loaded appPolicies=${rules.appPoliciesByIdentity.size} targetPolicy=${rules.appPoliciesByIdentity.containsKey(identity.identityKey)}"
            )
            val builtContext = buildContext(
                nowMillis = nowMillis,
                event = event,
                identity = identity,
                rules = rules
            )
            val currentContext = if (clearQuitPendingAfterCommit) {
                builtContext.copy(activeOverlayType = OverlayType.NONE)
            } else {
                builtContext
            }
            val previousContext = lastContext ?: currentContext.copy(
                eventType = EnforcementEventType.APP_FOREGROUND_ENTER,
                sliceStartedAtMillis = nowMillis,
                foregroundStartedAtMillis = nowMillis
            )
            val result = queue.submit(
                previousContext = previousContext,
                currentContext = currentContext
            )
            V2DiagnosticBridge.log(
                service,
                "v2",
                "decision package=$packageName action=${result.decision.action} reason=${result.decision.reasonCode} overlay=${result.decision.overlayType} fatal=${currentContext.systemHealthState.fatalIssues}"
            )
            val executionResult = executeDecision(result.decision, currentContext)
            overlayState.activeOverlayType = overlayTypeAfter(
                executionResult = executionResult,
                previousOverlayType = overlayState.activeOverlayType
            )
            V2DiagnosticBridge.log(
                service,
                "v2",
                "execution package=$packageName result=${executionResult.logName()} action=${executionResult.decision.action}"
            )
            val nextSliceContext = currentContext.copy(
                activeOverlayType = overlayState.activeOverlayType,
                sliceStartedAtMillis = nowMillis
            )
            decisionEventRecorder.record(nextSliceContext, result.decision, bootMarker)
            stateRepository.save(nextSliceContext.toPersistentState(bootMarker))
            lastContext = nextSliceContext
            if (clearQuitPendingAfterCommit) {
                overlayState.quitPendingIdentityKey = null
            }
        }

        suspend fun runTicks() {
            while (runtime === this) {
                delay(TICK_INTERVAL_MILLIS)
                enqueueTick()
            }
        }

        private suspend fun processTick(nowMillis: Long) {
            val last = lastContext ?: return
            val rules = try {
                ruleRepository.load()
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "tick rules unavailable identity=${last.foregroundApp.identityKey} error=${throwable.message}"
                )
                handleUnavailableRules(last.foregroundApp, nowMillis)
                return
            }
            val currentContext = buildTickContext(nowMillis, last, rules)
            val result = queue.submit(previousContext = last, currentContext = currentContext)
            V2DiagnosticBridge.log(
                service,
                "v2",
                "tick decision package=${currentContext.foregroundApp.rawPackageName} action=${result.decision.action} reason=${result.decision.reasonCode} overlay=${result.decision.overlayType}"
            )
            val executionResult = executeDecision(result.decision, currentContext)
            overlayState.activeOverlayType = overlayTypeAfter(executionResult, overlayState.activeOverlayType)
            val nextSliceContext = currentContext.copy(
                activeOverlayType = overlayState.activeOverlayType,
                sliceStartedAtMillis = nowMillis
            )
            decisionEventRecorder.record(nextSliceContext, result.decision, bootMarker)
            stateRepository.save(nextSliceContext.toPersistentState(bootMarker))
            lastContext = nextSliceContext
        }

        private fun processOverlayAction(action: RuntimeOverlayAction, nowMillis: Long) {
            when (action) {
                is RuntimeOverlayAction.Primary -> {
                    passRepository.save(
                        ActivePass(
                            identityKey = action.decision.targetIdentity.identityKey,
                            untilMillis = nowMillis + action.decision.durationMillis
                        )
                    )
                    overlayState.quitPendingIdentityKey = null
                    overlayState.challengeTransitionPendingIdentityKey = null
                    overlayState.activeOverlayType = OverlayType.NONE
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "challenge passed activePass package=${action.decision.targetIdentity.rawPackageName} durationMillis=${action.decision.durationMillis}"
                    )
                    MascotSoundPlayer.playChallengePassed(service)
                }
                is RuntimeOverlayAction.Quit -> {
                    passRepository.clear()
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "overlay quit fail-closed-home package=${action.decision.targetIdentity.rawPackageName}"
                    )
                }
            }
            lastContext?.let { last ->
                val nextSliceContext = last.copy(
                    nowMillis = nowMillis,
                    activeOverlayType = overlayState.activeOverlayType,
                    sliceStartedAtMillis = nowMillis
                )
                stateRepository.save(nextSliceContext.toPersistentState(bootMarker))
                lastContext = nextSliceContext
            }
        }

        private suspend fun processScreenEvent(action: String, nowMillis: Long) {
            val screenEvent = screenEventFor(action) ?: return
            val last = lastContext
            if (last == null) {
                stateRepository.save(
                    PersistentRuntimeState(
                        lastEventTimeMillis = nowMillis,
                        lastForegroundIdentityKey = "",
                        lastRawPackageName = "",
                        lastScreenState = screenEvent.screenState,
                        bootMarker = bootMarker
                    )
                )
                if (screenEvent.resetPhoneSession) phoneUsageRepository.resetSession()
                V2DiagnosticBridge.log(service, "v2", "screen event stored without foreground action=$action")
                return
            }
            val rules = try {
                ruleRepository.load()
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "screen rules unavailable action=$action identity=${last.foregroundApp.identityKey} error=${throwable.message}"
                )
                handleUnavailableRules(last.foregroundApp, nowMillis)
                return
            }
            val currentContext = buildScreenContext(
                nowMillis, last, rules, screenEvent.eventType, screenEvent.screenState
            )
            val result = queue.submit(previousContext = last, currentContext = currentContext)
            V2DiagnosticBridge.log(
                service,
                "v2",
                "screen decision action=$action package=${currentContext.foregroundApp.rawPackageName} decision=${result.decision.action} reason=${result.decision.reasonCode}"
            )
            val executionResult = executeDecision(result.decision, currentContext)
            overlayState.activeOverlayType = overlayTypeAfter(executionResult, overlayState.activeOverlayType)
            if (screenEvent.resetPhoneSession) {
                phoneUsageRepository.resetSession()
                V2DiagnosticBridge.log(service, "v2", "phone session reset by screen action=$action")
            }
            val nextSliceContext = currentContext.copy(
                activeOverlayType = overlayState.activeOverlayType,
                sliceStartedAtMillis = nowMillis
            )
            decisionEventRecorder.record(nextSliceContext, result.decision, bootMarker)
            stateRepository.save(nextSliceContext.toPersistentState(bootMarker))
            lastContext = nextSliceContext
        }

        private fun executeDecision(
            decision: EnforcementDecision,
            context: EnforcementContext
        ): EnforcementExecutionResult {
            if (shouldSuppressQuitPendingChallenge(
                    pendingIdentityKey = overlayState.quitPendingIdentityKey
                        ?: overlayState.challengeTransitionPendingIdentityKey,
                    targetIdentityKey = decision.targetIdentity.identityKey,
                    action = decision.action
                )
            ) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "suppress pending-quit challenge package=${decision.targetIdentity.rawPackageName}"
                )
                return EnforcementExecutionResult.NoAction(decision)
            }
            val executionResult = executor.execute(decision, context)
            if (executionResult is EnforcementExecutionResult.OverlayShown &&
                shouldSuppressQuitPendingChallenge(
                    pendingIdentityKey = overlayState.quitPendingIdentityKey
                        ?: overlayState.challengeTransitionPendingIdentityKey,
                    targetIdentityKey = decision.targetIdentity.identityKey,
                    action = decision.action
                )
            ) {
                executor.removeOverlays()
                return EnforcementExecutionResult.NoAction(decision)
            }
            return executionResult
        }

        private fun isOwnOverlayWindowEvent(event: AccessibilityEvent, packageName: String): Boolean {
            if (overlayState.activeOverlayType == OverlayType.NONE) return false
            if (packageName != service.packageName) return false
            val className = event.className?.toString().orEmpty()
            return className.isBlank() || !className.startsWith(service.packageName)
        }

        private fun buildContext(
            nowMillis: Long,
            event: AccessibilityEvent,
            identity: AppIdentity,
            rules: RuleSnapshot
        ): EnforcementContext {
            val last = lastContext
            val foregroundChanged = last?.foregroundApp?.identityKey != identity.identityKey
            val page = pageExtractor.extract(event, service.rootInActiveWindow)
            val pageState = pageContextTracker.update(
                identity = identity,
                pageSnapshot = page,
                nowMillis = nowMillis
            )
            val appUsage = appUsageRepository.load(identity.identityKey)
            val phoneUsage = phoneUsageRepository.load()
            val activePass = passRepository.load()
            val systemHealth = healthReader.read(
                notificationListenerConnected = false,
                foregroundServiceRunning = V2EnforcementForegroundService.isRunning()
            ).copy(
                overlayPermissionGranted = true
            )
            return EnforcementContext(
                nowMillis = nowMillis,
                eventType = if (foregroundChanged) {
                    EnforcementEventType.APP_FOREGROUND_ENTER
                } else {
                    EnforcementEventType.PAGE_CHANGED
                },
                foregroundApp = identity,
                previousForegroundApp = last?.foregroundApp,
                currentPage = pageState.pageSnapshot,
                pageContextMissingSinceMillis = pageState.missingSinceMillis,
                screenState = ScreenState.UNLOCKED,
                activeOverlayType = overlayState.activeOverlayType,
                foregroundStartedAtMillis = if (foregroundChanged) {
                    nowMillis
                } else {
                    last?.foregroundStartedAtMillis ?: nowMillis
                },
                sliceStartedAtMillis = if (foregroundChanged) {
                    nowMillis
                } else {
                    last?.sliceStartedAtMillis ?: nowMillis
                },
                ruleSnapshot = rules,
                usageSnapshot = UsageSnapshot(
                    appDailyUsedMillis = appUsage.usedMillis + appUsage.pendingOfflineGapMillis,
                    appSessionUsedMillis = appUsage.sessionUsedMillis,
                    appContinuousUsedMillis = appUsage.continuousUsedMillis,
                    appDailyOpenCount = appUsage.openCount,
                    phoneDailyUsedMillis = phoneUsage.dailyUsedMillis + phoneUsage.pendingOfflineGapMillis,
                    phoneSessionUsedMillis = phoneUsage.sessionUsedMillis,
                    sleepLockActive = sleepScheduleEvaluator.isSleepActive(rules.sleepPolicy, nowMillis)
                ),
                systemHealthState = systemHealth,
                safeZonePolicy = AndroidSafeZonePolicyFactory.create(service),
                activePass = activePass,
                activeCooldown = null
            )
        }

        private fun buildTickContext(
            nowMillis: Long,
            previousContext: EnforcementContext,
            rules: RuleSnapshot
        ): EnforcementContext {
            val identity = previousContext.foregroundApp
            val appUsage = appUsageRepository.load(identity.identityKey)
            val phoneUsage = phoneUsageRepository.load()
            val activePass = passRepository.load()?.let { pass ->
                if (pass.untilMillis <= nowMillis) {
                    passRepository.clear()
                    V2DiagnosticBridge.log(
                        service,
                        "v2",
                        "active pass expired identity=${pass.identityKey} untilMillis=${pass.untilMillis}"
                    )
                    null
                } else {
                    pass
                }
            }
            val systemHealth = healthReader.read(
                notificationListenerConnected = false,
                foregroundServiceRunning = V2EnforcementForegroundService.isRunning()
            ).copy(
                overlayPermissionGranted = true
            )
            return EnforcementContext(
                nowMillis = nowMillis,
                eventType = EnforcementEventType.TICK,
                foregroundApp = identity,
                previousForegroundApp = previousContext.previousForegroundApp,
                currentPage = previousContext.currentPage,
                pageContextMissingSinceMillis = previousContext.pageContextMissingSinceMillis,
                screenState = previousContext.screenState,
                activeOverlayType = overlayState.activeOverlayType,
                foregroundStartedAtMillis = previousContext.foregroundStartedAtMillis,
                sliceStartedAtMillis = previousContext.sliceStartedAtMillis,
                ruleSnapshot = rules,
                usageSnapshot = UsageSnapshot(
                    appDailyUsedMillis = appUsage.usedMillis + appUsage.pendingOfflineGapMillis,
                    appSessionUsedMillis = appUsage.sessionUsedMillis,
                    appContinuousUsedMillis = appUsage.continuousUsedMillis,
                    appDailyOpenCount = appUsage.openCount,
                    phoneDailyUsedMillis = phoneUsage.dailyUsedMillis + phoneUsage.pendingOfflineGapMillis,
                    phoneSessionUsedMillis = phoneUsage.sessionUsedMillis,
                    sleepLockActive = sleepScheduleEvaluator.isSleepActive(rules.sleepPolicy, nowMillis)
                ),
                systemHealthState = systemHealth,
                safeZonePolicy = AndroidSafeZonePolicyFactory.create(service),
                activePass = activePass,
                activeCooldown = null
            )
        }

        private fun buildScreenContext(
            nowMillis: Long,
            previousContext: EnforcementContext,
            rules: RuleSnapshot,
            eventType: EnforcementEventType,
            screenState: ScreenState
        ): EnforcementContext =
            buildTickContext(
                nowMillis = nowMillis,
                previousContext = previousContext,
                rules = rules
            ).copy(
                eventType = eventType,
                screenState = screenState
            )

        private fun handleUnavailableRules(identity: AppIdentity, nowMillis: Long) {
            val safeZonePolicy = AndroidSafeZonePolicyFactory.create(service)
            if (safeZonePolicy.isSafe(identity)) {
                stateRepository.save(
                    PersistentRuntimeState(
                        lastEventTimeMillis = nowMillis,
                        lastForegroundIdentityKey = identity.identityKey,
                        lastRawPackageName = identity.rawPackageName,
                        lastScreenState = ScreenState.UNLOCKED,
                        bootMarker = bootMarker
                    )
                )
                return
            }
            if (setupStateRepository.isSetupCompleted()) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "rules unavailable fail-closed-home package=${identity.rawPackageName}"
                )
                AccessibilityHomeActionPerformer(service).performHome()
                stateRepository.save(
                    PersistentRuntimeState(
                        lastEventTimeMillis = nowMillis,
                        lastForegroundIdentityKey = identity.identityKey,
                        lastRawPackageName = identity.rawPackageName,
                        lastScreenState = ScreenState.UNLOCKED,
                        bootMarker = bootMarker
                    )
                )
                return
            }
            service.startActivity(
                Intent(service, V2RequiredSetupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            stateRepository.save(
                PersistentRuntimeState(
                    lastEventTimeMillis = nowMillis,
                    lastForegroundIdentityKey = identity.identityKey,
                    lastRawPackageName = identity.rawPackageName,
                    lastScreenState = ScreenState.UNLOCKED,
                    bootMarker = bootMarker
                )
            )
        }

        private fun EnforcementContext.toPersistentState(bootMarker: String): PersistentRuntimeState =
            PersistentRuntimeState(
                lastEventTimeMillis = nowMillis,
                lastForegroundIdentityKey = foregroundApp.identityKey,
                lastRawPackageName = foregroundApp.rawPackageName,
                lastScreenState = screenState,
                bootMarker = bootMarker
            )

        companion object {
            fun create(service: BusterAccessibilityService): RuntimeHolder {
                val safeZonePolicy = AndroidSafeZonePolicyFactory.create(service)
                val appUsageRepository = LocalAppUsageRepository(service)
                val phoneUsageRepository = LocalPhoneUsageRepository(service)
                val eventStore = LocalEventStore(service)
                val stateRepository = LocalStateRepository(service)
                val commitWriter = UsageCommitWriter(appUsageRepository, phoneUsageRepository)
                val passRepository = LocalPassRepository(service)
                val overlayState = RuntimeOverlayState()
                passRepository.clear()
                V2DiagnosticBridge.log(service, "v2", "cleared active pass on runtime create")
                lateinit var holder: RuntimeHolder
                val runtimeExecutor = RuntimeExecutor(service) { action ->
                    holder.enqueueOverlayAction(action)
                }
                holder = RuntimeHolder(
                    service = service,
                    ruleRepository = LocalRuleRepository(service),
                    appUsageRepository = appUsageRepository,
                    phoneUsageRepository = phoneUsageRepository,
                    passRepository = passRepository,
                    setupStateRepository = LocalSetupStateRepository(service),
                    stateRepository = stateRepository,
                    eventStore = eventStore,
                    identityResolver = AndroidAppIdentityResolver(service, safeZonePolicy),
                    pageExtractor = AccessibilityPageSnapshotExtractor(),
                    pageContextTracker = PageContextTracker(),
                    healthReader = AndroidSystemHealthReader(
                        context = service,
                        accessibilityServiceComponent = ComponentName(service, BusterAccessibilityService::class.java)
                    ),
                    sleepScheduleEvaluator = SleepScheduleEvaluator(),
                    queue = SingleThreadEnforcementQueue(
                        usageCommitWriter = commitWriter
                    ),
                    executor = runtimeExecutor,
                    decisionEventRecorder = DecisionEventRecorder(eventStore),
                    bootMarker = stateRepository.newBootMarker(),
                    overlayState = overlayState
                )
                return holder
            }
        }

        private fun overlayTypeAfter(
            executionResult: EnforcementExecutionResult,
            previousOverlayType: OverlayType
        ): OverlayType =
            when (executionResult) {
                is EnforcementExecutionResult.OverlayShown -> executionResult.decision.overlayType
                is EnforcementExecutionResult.HomeFailed,
                is EnforcementExecutionResult.HomeSent,
                is EnforcementExecutionResult.HomeSuppressed -> OverlayType.NONE
                is EnforcementExecutionResult.NoAction ->
                    when (executionResult.decision.action) {
                        EnforcementAction.ALLOW,
                        EnforcementAction.NO_OP -> OverlayType.NONE
                        else -> previousOverlayType
                    }
            }

        private fun screenEventFor(action: String): ScreenRuntimeEvent? =
            when (action) {
                Intent.ACTION_SCREEN_OFF -> ScreenRuntimeEvent(
                    eventType = EnforcementEventType.SCREEN_OFF,
                    screenState = ScreenState.OFF,
                    resetPhoneSession = true
                )
                Intent.ACTION_SCREEN_ON -> ScreenRuntimeEvent(
                    eventType = EnforcementEventType.SCREEN_ON,
                    screenState = ScreenState.ON,
                    resetPhoneSession = true
                )
                Intent.ACTION_USER_PRESENT -> ScreenRuntimeEvent(
                    eventType = EnforcementEventType.USER_PRESENT,
                    screenState = ScreenState.UNLOCKED,
                    resetPhoneSession = true
                )
                else -> null
            }

        private data class ScreenRuntimeEvent(
            val eventType: EnforcementEventType,
            val screenState: ScreenState,
            val resetPhoneSession: Boolean
        )
    }
}

class RuntimeExecutor(
    service: BusterAccessibilityService,
    private val overlayActionSink: (RuntimeOverlayAction) -> Unit
) {
    private val homeActionPerformer = AccessibilityHomeActionPerformer(service)
    private val overlayController = SimpleOverlayController(
        context = service,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        actionHandler = object : OverlayActionHandler {
            override fun onPrimaryAction(decision: com.addictionbuster.enforcement.EnforcementDecision) {
                if (decision.action != EnforcementAction.SHOW_APP_CHALLENGE) return
                if (decision.durationMillis <= 0L) return
                overlayActionSink(RuntimeOverlayAction.Primary(decision))
            }

            override fun onQuitAction(decision: com.addictionbuster.enforcement.EnforcementDecision) {
                overlayActionSink(RuntimeOverlayAction.Quit(decision))
            }
        }
    )
    private val executor = EnforcementExecutor(
        context = service,
        overlayController = overlayController,
        homeActionPerformer = homeActionPerformer,
        overlayPermissionChecker = object : OverlayPermissionChecker {
            override fun canShowOverlay(): Boolean = true
        }
    )

    fun execute(
        decision: com.addictionbuster.enforcement.EnforcementDecision,
        context: EnforcementContext
    ): EnforcementExecutionResult = executor.execute(decision, context)

    fun removeOverlays() {
        overlayController.removeAll()
    }
}

class RuntimeOverlayState {
    @Volatile
    var activeOverlayType: OverlayType = OverlayType.NONE

    @Volatile
    var quitPendingIdentityKey: String? = null

    @Volatile
    var challengeTransitionPendingIdentityKey: String? = null
}

private sealed interface RuntimeInput {
    val receivedAtMillis: Long

    data class Window(
        val event: AccessibilityEvent,
        val eventType: Int,
        val packageName: String,
        override val receivedAtMillis: Long
    ) : RuntimeInput

    data class Screen(
        val action: String,
        override val receivedAtMillis: Long
    ) : RuntimeInput

    data class Tick(override val receivedAtMillis: Long) : RuntimeInput

    data class Overlay(
        val action: RuntimeOverlayAction,
        override val receivedAtMillis: Long
    ) : RuntimeInput
}

sealed interface RuntimeOverlayAction {
    val decision: EnforcementDecision

    data class Primary(override val decision: EnforcementDecision) : RuntimeOverlayAction
    data class Quit(override val decision: EnforcementDecision) : RuntimeOverlayAction
}

internal fun shouldSuppressQuitPendingChallenge(
    pendingIdentityKey: String?,
    targetIdentityKey: String,
    action: EnforcementAction
): Boolean =
    pendingIdentityKey != null &&
            pendingIdentityKey == targetIdentityKey &&
            action == EnforcementAction.SHOW_APP_CHALLENGE

internal class OrderedRuntimeEventProcessor<T>(
    scope: CoroutineScope,
    private val handler: suspend (T) -> Unit,
    private val failureHandler: (T, Throwable) -> Unit = { _, _ -> }
) {
    private val channel = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in channel) {
                try {
                    handler(event)
                } catch (throwable: Throwable) {
                    try {
                        failureHandler(event, throwable)
                    } catch (_: Throwable) {
                        // Keep the single consumer alive even if diagnostics fail.
                    }
                }
            }
        }
    }

    fun submit(event: T): Boolean = channel.trySend(event).isSuccess

    fun close() {
        channel.close()
    }
}

@Suppress("DEPRECATION")
private fun copyAccessibilityEvent(event: AccessibilityEvent): AccessibilityEvent =
    AccessibilityEvent.obtain(event)

@Suppress("DEPRECATION")
private fun recycleAccessibilityEvent(event: AccessibilityEvent) = event.recycle()

private fun EnforcementExecutionResult.logName(): String =
    when (this) {
        is EnforcementExecutionResult.HomeFailed -> "HomeFailed"
        is EnforcementExecutionResult.HomeSent -> "HomeSent"
        is EnforcementExecutionResult.HomeSuppressed -> "HomeSuppressed"
        is EnforcementExecutionResult.NoAction -> "NoAction"
        is EnforcementExecutionResult.OverlayShown -> "OverlayShown"
    }
