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
        scope.launch {
            try {
                holder.processEvent(event, packageName)
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "runtime exception package=$packageName type=${event.eventType} error=${throwable.message}"
                )
                Log.w(TAG, "failed to process v2 accessibility event package=$packageName", throwable)
            }
        }
    }

    @JvmStatic
    fun onScreenEvent(service: BusterAccessibilityService, action: String?) {
        val holder = runtime ?: RuntimeHolder.create(service).also {
            it.queue.start()
            runtime = it
            scope.launch { it.runTicks() }
        }
        scope.launch {
            try {
                holder.processScreenEvent(action.orEmpty(), System.currentTimeMillis())
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(service, "v2", "screen event exception action=$action error=${throwable.message}")
                Log.w(TAG, "failed to process v2 screen event action=$action", throwable)
            }
        }
    }

    @JvmStatic
    fun onDestroy() {
        runtime?.executor?.removeOverlays()
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

        suspend fun processEvent(event: AccessibilityEvent, packageName: String) {
            val nowMillis = System.currentTimeMillis()
            if (isOwnOverlayWindowEvent(event, packageName)) {
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "ignored own overlay window event type=${event.eventType} class=${event.className ?: ""} activeOverlay=${overlayState.activeOverlayType}"
                )
                return
            }
            val identity = identityResolver.resolve(packageName)
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
            val currentContext = buildContext(
                nowMillis = nowMillis,
                event = event,
                identity = identity,
                rules = rules
            )
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
            val executionResult = executor.execute(result.decision, currentContext)
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
        }

        suspend fun runTicks() {
            while (runtime === this) {
                delay(TICK_INTERVAL_MILLIS)
                try {
                    processTick(System.currentTimeMillis())
                } catch (throwable: Throwable) {
                    V2DiagnosticBridge.log(service, "v2", "tick exception error=${throwable.message}")
                    Log.w(TAG, "failed to process v2 tick", throwable)
                }
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
            val currentContext = buildTickContext(
                nowMillis = nowMillis,
                previousContext = last,
                rules = rules
            )
            val result = queue.submit(
                previousContext = last,
                currentContext = currentContext
            )
            V2DiagnosticBridge.log(
                service,
                "v2",
                "tick decision package=${currentContext.foregroundApp.rawPackageName} action=${result.decision.action} reason=${result.decision.reasonCode} overlay=${result.decision.overlayType}"
            )
            val executionResult = executor.execute(result.decision, currentContext)
            overlayState.activeOverlayType = overlayTypeAfter(
                executionResult = executionResult,
                previousOverlayType = overlayState.activeOverlayType
            )
            val nextSliceContext = currentContext.copy(
                activeOverlayType = overlayState.activeOverlayType,
                sliceStartedAtMillis = nowMillis
            )
            decisionEventRecorder.record(nextSliceContext, result.decision, bootMarker)
            stateRepository.save(nextSliceContext.toPersistentState(bootMarker))
            lastContext = nextSliceContext
        }

        suspend fun processScreenEvent(action: String, nowMillis: Long) {
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
                if (screenEvent.resetPhoneSession) {
                    phoneUsageRepository.resetSession()
                }
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
                nowMillis = nowMillis,
                previousContext = last,
                rules = rules,
                eventType = screenEvent.eventType,
                screenState = screenEvent.screenState
            )
            val result = queue.submit(
                previousContext = last,
                currentContext = currentContext
            )
            V2DiagnosticBridge.log(
                service,
                "v2",
                "screen decision action=$action package=${currentContext.foregroundApp.rawPackageName} decision=${result.decision.action} reason=${result.decision.reasonCode}"
            )
            val executionResult = executor.execute(result.decision, currentContext)
            overlayState.activeOverlayType = overlayTypeAfter(
                executionResult = executionResult,
                previousOverlayType = overlayState.activeOverlayType
            )
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
                val runtimeExecutor = RuntimeExecutor(service, passRepository, overlayState)
                return RuntimeHolder(
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
    passRepository: LocalPassRepository,
    private val overlayState: RuntimeOverlayState
) {
    private val homeActionPerformer = AccessibilityHomeActionPerformer(service)
    private val overlayController = SimpleOverlayController(
        context = service,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        actionHandler = object : OverlayActionHandler {
            override fun onPrimaryAction(decision: com.addictionbuster.enforcement.EnforcementDecision) {
                if (decision.action != EnforcementAction.SHOW_APP_CHALLENGE) return
                if (decision.durationMillis <= 0L) return
                passRepository.save(
                    ActivePass(
                        identityKey = decision.targetIdentity.identityKey,
                        untilMillis = System.currentTimeMillis() + decision.durationMillis
                    )
                )
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "challenge passed activePass package=${decision.targetIdentity.rawPackageName} durationMillis=${decision.durationMillis}"
                )
                MascotSoundPlayer.playChallengePassed(service)
                overlayState.activeOverlayType = OverlayType.NONE
            }

            override fun onQuitAction(decision: com.addictionbuster.enforcement.EnforcementDecision) {
                passRepository.clear()
                V2DiagnosticBridge.log(
                    service,
                    "v2",
                    "overlay quit fail-closed-home package=${decision.targetIdentity.rawPackageName}"
                )
                homeActionPerformer.performHome()
                overlayState.activeOverlayType = OverlayType.NONE
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
        overlayState.activeOverlayType = OverlayType.NONE
    }
}

class RuntimeOverlayState {
    @Volatile
    var activeOverlayType: OverlayType = OverlayType.NONE
}

private fun EnforcementExecutionResult.logName(): String =
    when (this) {
        is EnforcementExecutionResult.HomeFailed -> "HomeFailed"
        is EnforcementExecutionResult.HomeSent -> "HomeSent"
        is EnforcementExecutionResult.HomeSuppressed -> "HomeSuppressed"
        is EnforcementExecutionResult.NoAction -> "NoAction"
        is EnforcementExecutionResult.OverlayShown -> "OverlayShown"
    }
