package com.addictionbuster.enforcement.sleep

import com.addictionbuster.enforcement.SleepPolicy
import com.addictionbuster.enforcement.SleepWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SleepScheduleEvaluatorTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val evaluator = SleepScheduleEvaluator(zoneId)

    @Test
    fun sameDayWindowUsesInclusiveStartAndExclusiveEnd() {
        val policy = SleepPolicy(
            enabled = true,
            windows = listOf(
                SleepWindow(
                    startMinuteOfDay = 9 * 60,
                    endMinuteOfDay = 17 * 60,
                    activeDays = setOf(1)
                )
            )
        )

        assertTrue(evaluator.isSleepActive(policy, millis("2026-06-08T09:00:00")))
        assertTrue(evaluator.isSleepActive(policy, millis("2026-06-08T16:59:00")))
        assertFalse(evaluator.isSleepActive(policy, millis("2026-06-08T17:00:00")))
    }

    @Test
    fun crossDayWindowIsActiveBeforeMidnightAndAfterMidnight() {
        val policy = SleepPolicy(
            enabled = true,
            windows = listOf(
                SleepWindow(
                    startMinuteOfDay = 23 * 60,
                    endMinuteOfDay = 7 * 60,
                    activeDays = setOf(1)
                )
            )
        )

        assertTrue(evaluator.isSleepActive(policy, millis("2026-06-08T23:30:00")))
        assertTrue(evaluator.isSleepActive(policy, millis("2026-06-09T06:30:00")))
        assertFalse(evaluator.isSleepActive(policy, millis("2026-06-09T08:00:00")))
    }

    @Test
    fun crossDayWindowDoesNotLeakIntoUnselectedDays() {
        val policy = SleepPolicy(
            enabled = true,
            windows = listOf(
                SleepWindow(
                    startMinuteOfDay = 23 * 60,
                    endMinuteOfDay = 7 * 60,
                    activeDays = setOf(1)
                )
            )
        )

        assertFalse(evaluator.isSleepActive(policy, millis("2026-06-08T06:30:00")))
        assertFalse(evaluator.isSleepActive(policy, millis("2026-06-09T23:30:00")))
    }

    @Test
    fun disabledPolicyIsNeverActive() {
        val policy = SleepPolicy(
            enabled = false,
            windows = emptyList()
        )

        assertFalse(evaluator.isSleepActive(policy, millis("2026-06-08T23:30:00")))
    }

    private fun millis(value: String): Long =
        LocalDateTime.parse(value).atZone(zoneId).toInstant().toEpochMilli()
}
