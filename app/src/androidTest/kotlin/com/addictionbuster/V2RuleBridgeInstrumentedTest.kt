package com.addictionbuster

import androidx.test.platform.app.InstrumentationRegistry
import com.addictionbuster.enforcement.ActivePass
import com.addictionbuster.enforcement.storage.LocalPassRepository
import com.addictionbuster.enforcement.storage.LocalRuleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class V2RuleBridgeInstrumentedTest {
    @Before
    fun setUp() {
        File(context().filesDir, "enforcement_v2").deleteRecursively()
    }

    @Test
    fun saveAppRuleWritesV2RuleSnapshot() {
        val rule = AppRule(
            500,
            10,
            15,
            6,
            2,
            9,
            ""
        )

        V2RuleBridge.saveAppRule(context(), "tv.danmaku.bili", rule)

        val snapshot = LocalRuleRepository(context()).load()
        val appPolicy = snapshot.requireAppPolicyFor("tv.danmaku.bili")
        assertTrue(appPolicy.enabled)
        assertTrue(appPolicy.challengeEnabled)
        assertEquals(500L * 60_000L, appPolicy.dailyLimitMillis)
        assertEquals(0L, appPolicy.sessionLimitMillis)
        assertEquals(10L * 60_000L, appPolicy.passthroughMillis)
        assertEquals(15L * 1000L, appPolicy.challengeWaitMillis)
        assertEquals(6, appPolicy.challengeRequiredTaps)
        assertEquals(2, appPolicy.challengeHiddenCount)
        assertEquals(9L * 1000L, appPolicy.challengeHiddenMillis)
        assertEquals("", appPolicy.challengeConfirmText)
    }

    @Test
    fun saveAppRuleClearsExistingActivePass() {
        val passRepository = LocalPassRepository(context())
        passRepository.save(
            ActivePass(
                identityKey = "com.dragon.read",
                untilMillis = System.currentTimeMillis() + 10L * 60_000L
            )
        )

        V2RuleBridge.saveAppRule(
            context(),
            "com.dragon.read",
            AppRule(1000, 10, 15, 2, 1, 1, "")
        )

        assertNull(passRepository.load())
    }

    @Test
    fun saveAppRuleRebuildsMalformedV2RuleSnapshot() {
        val directory = File(context().filesDir, "enforcement_v2")
        directory.mkdirs()
        File(directory, "rules.json").writeText(
            JSONObject()
                .put(
                    "globalPolicy",
                    JSONObject()
                        .put("phoneDailyLimitMillis", 0L)
                        .put("phoneSessionLimitMillis", 0L)
                        .put("countWhitelistIdentities", JSONArray())
                        .put("emergencyWhitelistIdentities", JSONArray())
                )
                .put(
                    "clonePolicy",
                    JSONObject()
                        .put("enabled", true)
                        .put("blockUnknownClones", true)
                        .put("blockKnownCloneContainers", true)
                        .put("allowManualCloneRules", false)
                        .put("knownContainerPackages", JSONArray())
                        .put("manualCloneIdentities", JSONArray())
                )
                .put("sleepPolicy", JSONObject().put("enabled", false).put("windows", JSONArray()))
                .put(
                    "appPolicies",
                    JSONArray().put(
                        JSONObject()
                            .put("identityKey", "tv.danmaku.bili")
                            .put("enabled", true)
                            .put("challengeEnabled", true)
                            .put("dailyLimitMillis", 60_000L)
                            .put("sessionLimitMillis", 60_000L)
                            .put("continuousUseLimitMillis", 0L)
                            .put("restRequiredMillis", 0L)
                            .put("dailyOpenLimit", 0)
                            .put("passthroughMillis", 60_000L)
                            .put("cooldownAfterUseMillis", 0L)
                            .put("cooldownAfterQuitMillis", 0L)
                            .put("countTowardsPhoneUsage", true)
                    )
                )
                .put("pagePolicies", JSONArray())
                .toString()
        )

        V2RuleBridge.saveAppRule(
            context(),
            "tv.danmaku.bili",
            AppRule(500, 10, 15, 6, 2, 9, "")
        )

        val appPolicy = LocalRuleRepository(context()).load().requireAppPolicyFor("tv.danmaku.bili")
        assertEquals(15L * 1000L, appPolicy.challengeWaitMillis)
        assertEquals(6, appPolicy.challengeRequiredTaps)
    }

    @Test
    fun savePhoneLimitsWritesV2GlobalPolicyWithoutDroppingAppRules() {
        V2RuleBridge.saveAppRule(
            context(),
            "tv.danmaku.bili",
            AppRule(500, 10, 15, 6, 2, 9, "")
        )

        V2RuleBridge.savePhoneLimits(context(), 120, 30)

        val snapshot = LocalRuleRepository(context()).load()
        assertEquals(120L * 60_000L, snapshot.globalPolicy.phoneDailyLimitMillis)
        assertEquals(30L * 60_000L, snapshot.globalPolicy.phoneSessionLimitMillis)
        assertTrue(snapshot.appPoliciesByIdentity.containsKey("tv.danmaku.bili"))
    }

    @Test
    fun savePhoneWhitelistWritesV2CountWhitelist() {
        V2RuleBridge.savePhoneWhitelist(
            context(),
            setOf("com.android.settings", "com.example.reader")
        )

        val snapshot = LocalRuleRepository(context()).load()
        assertEquals(
            setOf("com.android.settings", "com.example.reader"),
            snapshot.globalPolicy.countWhitelistIdentities
        )
    }

    private fun context() =
        InstrumentationRegistry.getInstrumentation().targetContext
}
