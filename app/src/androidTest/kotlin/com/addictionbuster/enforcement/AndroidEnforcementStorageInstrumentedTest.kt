package com.addictionbuster.enforcement

import androidx.test.platform.app.InstrumentationRegistry
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository
import com.addictionbuster.enforcement.storage.LocalRuleRepository
import com.addictionbuster.enforcement.storage.LocalStateRepository
import com.addictionbuster.enforcement.storage.PersistentRuntimeState
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

    private fun context() =
        InstrumentationRegistry.getInstrumentation().targetContext
}
