package com.addictionbuster.enforcement.sleep

import com.addictionbuster.enforcement.SleepPolicy
import com.addictionbuster.enforcement.SleepWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepScheduleEvaluator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun isSleepActive(policy: SleepPolicy, nowMillis: Long): Boolean {
        if (!policy.enabled) return false
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        return policy.windows.any { window -> window.isActiveAt(now) }
    }

    private fun SleepWindow.isActiveAt(now: ZonedDateTime): Boolean {
        val minuteOfDay = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.minusDays(1).dayOfWeek.value
        return if (startMinuteOfDay < endMinuteOfDay) {
            today in activeDays && minuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else {
            (today in activeDays && minuteOfDay >= startMinuteOfDay) ||
                    (yesterday in activeDays && minuteOfDay < endMinuteOfDay)
        }
    }
}
