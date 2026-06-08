package com.addictionbuster

import androidx.test.platform.app.InstrumentationRegistry
import com.addictionbuster.enforcement.storage.LocalRuleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

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
        assertEquals(10L * 60_000L, appPolicy.sessionLimitMillis)
    }

    private fun context() =
        InstrumentationRegistry.getInstrumentation().targetContext
}
