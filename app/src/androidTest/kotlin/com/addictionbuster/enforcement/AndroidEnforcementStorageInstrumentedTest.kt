package com.addictionbuster.enforcement

import androidx.test.platform.app.InstrumentationRegistry
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository
import com.addictionbuster.enforcement.storage.LocalRuleRepository
import com.addictionbuster.enforcement.storage.LocalSetupStateRepository
import com.addictionbuster.enforcement.storage.LocalStateRepository
import com.addictionbuster.enforcement.storage.PersistentRuntimeState
import com.addictionbuster.enforcement.stats.EnforcementStatsAggregator
import com.addictionbuster.enforcement.stats.DecisionEventRecorder
import com.addictionbuster.enforcement.stats.SuccessfulInterceptionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class AndroidEnforcementStorageInstrumentedTest {
    @Before
    fun setUp() {
        File(context().filesDir, "enforcement_v2").deleteRecursively()
    }

    @Test
    fun testMissingV2RulesFailFast() {
        val repository = LocalRuleRepository(context())

        try {
            repository.load()
            fail("missing v2 rules must fail fast")
        } catch (expected: InvalidEnforcementContextException) {
            assertTrue(expected.message!!.contains("missing v2 rule snapshot"))
        }
    }

    @Test
    fun testEventStorePersistsStructuredEvent() {
        val store = LocalEventStore(context())

        val record = store.append(
            eventType = EnforcementEventType.DECISION_RECORDED,
            occurredAtMillis = 1000L,
            foregroundIdentityKey = "com.example.app",
            rawPackageName = "com.example.app",
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a",
            details = mapOf("reasonCode" to ReasonCode.APP_CHALLENGE_REQUIRED.name)
        )

        val loaded = store.readAll()
        assertEquals(1, loaded.size)
        assertEquals(record.eventId, loaded.first().eventId)
        assertEquals(ReasonCode.APP_CHALLENGE_REQUIRED.name, loaded.first().details["reasonCode"])
    }

    @Test
    fun testOfflineGapCanBeRecoveredFromPersistentState() {
        val repository = LocalStateRepository(context())
        repository.save(
            PersistentRuntimeState(
                lastEventTimeMillis = 1000L,
                lastForegroundIdentityKey = "com.example.app",
                lastRawPackageName = "com.example.app",
                lastScreenState = ScreenState.UNLOCKED,
                bootMarker = "boot-a"
            )
        )

        val gap = repository.computeOfflineGap(
            nowMillis = 4000L,
            currentBootMarker = "boot-a"
        )

        assertNotNull(gap)
        assertEquals(3000L, gap!!.durationMillis)
        assertEquals("com.example.app", gap.previousForegroundIdentityKey)
        assertFalse(gap.requiresUserConfirmation)
    }

    @Test
    fun testAppAndPhoneUsageArePersistedSeparately() {
        val appRepository = LocalAppUsageRepository(context())
        val phoneRepository = LocalPhoneUsageRepository(context())

        appRepository.addUsage("com.example.app", 2000L, dateKey = "2026-06-08")
        appRepository.incrementOpen("com.example.app", 2500L, dateKey = "2026-06-08")
        phoneRepository.addUsage(5000L, dateKey = "2026-06-08")

        val appUsage = appRepository.load("com.example.app", dateKey = "2026-06-08")
        val phoneUsage = phoneRepository.load(dateKey = "2026-06-08")
        assertEquals(2000L, appUsage.usedMillis)
        assertEquals(1, appUsage.openCount)
        assertEquals(5000L, phoneUsage.dailyUsedMillis)
    }

    @Test
    fun testPermissionControllerIsSafeZone() {
        val policy = AndroidSafeZonePolicyFactory.create(context())
        val identity = AppIdentity(
            rawPackageName = "com.google.android.permissioncontroller",
            canonicalPackageName = "com.google.android.permissioncontroller",
            displayName = "Permission Controller",
            identityType = IdentityType.NORMAL,
            cloneGroupId = "",
            containerPackageName = "",
            userHandleKey = "",
            isSystem = true,
            isLauncher = false,
            isEmergencyAllowed = false
        )

        assertTrue(policy.isSafe(identity))
    }

    @Test
    fun testSetupCompletionMarkerPersists() {
        val repository = LocalSetupStateRepository(context())

        assertFalse(repository.isSetupCompleted())
        repository.markSetupCompleted(completedAtMillis = 1234L)

        assertTrue(LocalSetupStateRepository(context()).isSetupCompleted())
    }

    @Test
    fun testDailyStatsAggregatesUsageAndEvents() {
        val appRepository = LocalAppUsageRepository(context())
        val phoneRepository = LocalPhoneUsageRepository(context())
        val eventStore = LocalEventStore(context())

        appRepository.addUsage("com.example.reader", 125_000L, dateKey = "2026-06-16")
        appRepository.incrementOpen("com.example.reader", 1000L, dateKey = "2026-06-16")
        appRepository.markOfflineGapPending("com.example.reader", 30_000L, dateKey = "2026-06-16")
        phoneRepository.addUsage(240_000L, dateKey = "2026-06-16")
        phoneRepository.markOfflineGapPending(60_000L, dateKey = "2026-06-16")
        eventStore.append(
            eventType = EnforcementEventType.DECISION_RECORDED,
            occurredAtMillis = 1_781_596_800_000L,
            foregroundIdentityKey = "com.example.reader",
            rawPackageName = "com.example.reader",
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a",
            details = mapOf("reasonCode" to ReasonCode.APP_SESSION_LIMIT_BLOCK.name)
        )
        eventStore.append(
            eventType = EnforcementEventType.DECISION_RECORDED,
            occurredAtMillis = 1_781_596_800_500L,
            foregroundIdentityKey = "com.example.reader",
            rawPackageName = "com.example.reader",
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a",
            details = mapOf("reasonCode" to ReasonCode.APP_CHALLENGE_REQUIRED.name)
        )
        val target = AppIdentity(
            rawPackageName = "com.example.reader",
            canonicalPackageName = "com.example.reader",
            displayName = "Reader",
            identityType = IdentityType.NORMAL,
            cloneGroupId = "",
            containerPackageName = "",
            userHandleKey = "",
            isSystem = false,
            isLauncher = false,
            isEmergencyAllowed = false
        )
        val outcome = SuccessfulInterceptionOutcome(
            overlaySessionId = "overlay-session-1",
            targetIdentity = target,
            triggerAction = EnforcementAction.SHOW_APP_CHALLENGE,
            triggerReasonCode = ReasonCode.APP_CHALLENGE_REQUIRED,
            overlayType = OverlayType.APP_CHALLENGE
        )
        val recorder = DecisionEventRecorder(eventStore)
        assertTrue(recorder.recordSuccessfulInterception(
            outcome = outcome,
            occurredAtMillis = 1_781_596_800_750L,
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a"
        ))
        assertFalse(recorder.recordSuccessfulInterception(
            outcome = outcome,
            occurredAtMillis = 1_781_596_800_900L,
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a"
        ))
        eventStore.append(
            eventType = EnforcementEventType.OFFLINE_GAP_DETECTED,
            occurredAtMillis = 1_781_596_801_000L,
            foregroundIdentityKey = "com.example.reader",
            rawPackageName = "com.example.reader",
            screenState = ScreenState.UNLOCKED,
            bootMarker = "boot-a",
            details = mapOf("durationMillis" to "60000")
        )

        val snapshot = EnforcementStatsAggregator(
            appUsageRepository = appRepository,
            phoneUsageRepository = phoneRepository,
            eventStore = eventStore
        ).dailySnapshot(dateKey = "2026-06-16")

        assertEquals(240_000L, snapshot.phoneUsage.dailyUsedMillis)
        assertEquals(60_000L, snapshot.phoneUsage.pendingOfflineGapMillis)
        assertEquals(1, snapshot.appUsages.size)
        assertEquals("com.example.reader", snapshot.appUsages.first().identityKey)
        assertEquals(125_000L, snapshot.appUsages.first().usedMillis)
        assertEquals(1, snapshot.appUsages.first().openCount)
        assertEquals(4, snapshot.eventStats.totalEvents)
        assertEquals(1, snapshot.eventStats.blockEvents)
        assertEquals(1, snapshot.eventStats.offlineGapEvents)
        assertEquals(60_000L, snapshot.eventStats.offlineGapMillis)
        val successful = eventStore.readAll().single {
            it.eventType == EnforcementEventType.INTERCEPTION_SUCCEEDED
        }
        assertEquals("USER_QUIT_TO_HOME", successful.details["outcome"])
        assertEquals("overlay-session-1", successful.details["overlaySessionId"])
        assertEquals(ReasonCode.APP_CHALLENGE_REQUIRED.name, successful.details["triggerReasonCode"])
    }

    private fun context() =
        InstrumentationRegistry.getInstrumentation().targetContext
}
