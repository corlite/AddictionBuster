package com.addictionbuster.enforcement.stats

import com.addictionbuster.enforcement.ReasonCode
import com.addictionbuster.enforcement.EnforcementAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessfulInterceptionPolicyTest {
    @Test
    fun enabledAppPolicyCountsOnlyAppScopedReasons() {
        assertTrue(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_APP_CHALLENGE,
                reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED
            )
        )
        assertTrue(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_APP_LIMIT_BLOCK,
                reasonCode = ReasonCode.APP_DAILY_LIMIT_BLOCK
            )
        )
        assertTrue(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_PAGE_BLOCK,
                reasonCode = ReasonCode.PAGE_KEYWORD_BLOCK
            )
        )
        assertTrue(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_APP_CHALLENGE,
                reasonCode = ReasonCode.PAGE_KEYWORD_BLOCK
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_PHONE_LIMIT_BLOCK,
                reasonCode = ReasonCode.PHONE_TOTAL_LIMIT_BLOCK
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_SLEEP_LOCK,
                reasonCode = ReasonCode.SLEEP_LOCK_BLOCK
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = false,
                action = EnforcementAction.SHOW_APP_CHALLENGE,
                reasonCode = ReasonCode.APP_CHALLENGE_REQUIRED
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.isEligible(
                appPolicyEnabled = true,
                action = EnforcementAction.SHOW_COOLDOWN_BLOCK,
                reasonCode = ReasonCode.PAGE_KEYWORD_BLOCK
            )
        )
    }

    @Test
    fun successfulOutcomeRequiresHomeActionToBeSent() {
        assertTrue(
            SuccessfulInterceptionPolicy.shouldRecordOutcome(
                overlaySessionEligible = true,
                homeSent = true
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.shouldRecordOutcome(
                overlaySessionEligible = true,
                homeSent = false
            )
        )
        assertFalse(
            SuccessfulInterceptionPolicy.shouldRecordOutcome(
                overlaySessionEligible = false,
                homeSent = true
            )
        )
    }
}
