package com.addictionbuster.enforcement.runtime

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.addictionbuster.BusterAccessibilityService
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
import com.addictionbuster.enforcement.executor.OverlayActionHandler
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
import kotlinx.coroutines.launch

object V2AccessibilityRuntime {
    private const val TAG = "V2AccessibilityRuntime"
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
        Log.d(TAG, "v2 accessibility runtime connected")
    }

    @JvmStatic
    fun onAccessibilityEvent(service: BusterAccessibilityService, event: AccessibilityEvent?) {
        val holder = runtime ?: RuntimeHolder.create(service).also {
            it.queue.start()
            runtime = it
        }
        if (event == null || (event.eventType and WINDOW_EVENT_TYPES) == 0) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        scope.launch {
            holder.processEvent(event, packageName)
        }
    }

    @JvmStatic
    fun onDestroy() {
        runtime?.executor?.removeOverlays()
        runtime = null
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
        private val bootMarker: String
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
            val identity = identityResolver.resolve(packageName)
            val rules = try {
                ruleRepository.load()
            } catch (throwable: Throwable) {
                handleMissingRules(identity, nowMillis)
                return
            }
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
            executor.execute(result.decision, currentContext)
            decisionEventRecorder.record(currentContext, result.decision, bootMarker)
            stateRepository.save(currentContext.toPersistentState(bootMarker))
            lastContext = currentContext
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
                activeOverlayType = OverlayType.NONE,
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
                systemHealthState = healthReader.read(
                    notificationListenerConnected = false,
                    foregroundServiceRunning = V2EnforcementForegroundService.isRunning()
                ),
                safeZonePolicy = AndroidSafeZonePolicyFactory.create(service),
                activePass = activePass,
                activeCooldown = null
            )
        }

        private fun handleMissingRules(identity: AppIdentity, nowMillis: Long) {
            val safeZonePolicy = AndroidSafeZonePolicyFactory.create(service)
            if (safeZonePolicy.isSafe(identity) || setupStateRepository.isSetupCompleted()) {
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
                val runtimeExecutor = RuntimeExecutor(service, passRepository)
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
                    bootMarker = stateRepository.newBootMarker()
                )
            }
        }
    }
}

class RuntimeExecutor(
    service: BusterAccessibilityService,
    passRepository: LocalPassRepository
) {
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
            }
        }
    )
    private val executor = EnforcementExecutor(
        context = service,
        overlayController = overlayController,
        homeActionPerformer = AccessibilityHomeActionPerformer(service)
    )

    fun execute(decision: com.addictionbuster.enforcement.EnforcementDecision, context: EnforcementContext) {
        executor.execute(decision, context)
    }

    fun removeOverlays() {
        overlayController.removeAll()
    }
}
