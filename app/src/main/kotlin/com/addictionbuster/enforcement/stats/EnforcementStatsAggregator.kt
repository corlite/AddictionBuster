package com.addictionbuster.enforcement.stats

import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.ReasonCode
import com.addictionbuster.enforcement.storage.EnforcementEventRecord
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EnforcementStatsAggregator(
    private val appUsageRepository: LocalAppUsageRepository,
    private val phoneUsageRepository: LocalPhoneUsageRepository,
    private val eventStore: LocalEventStore,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun dailySnapshot(dateKey: String = LocalDate.now(zoneId).toString()): DailyStatsSnapshot {
        val events = eventStore.readAll().filter { it.dateKey() == dateKey }
        return DailyStatsSnapshot(
            dateKey = dateKey,
            appUsages = appUsageRepository.listForDate(dateKey)
                .map {
                    AppUsageStats(
                        identityKey = it.identityKey,
                        usedMillis = it.usedMillis,
                        sessionUsedMillis = it.sessionUsedMillis,
                        continuousUsedMillis = it.continuousUsedMillis,
                        openCount = it.openCount,
                        pendingOfflineGapMillis = it.pendingOfflineGapMillis
                    )
                }
                .sortedByDescending { it.usedMillis + it.pendingOfflineGapMillis },
            phoneUsage = phoneUsageRepository.load(dateKey).let {
                PhoneUsageStats(
                    dailyUsedMillis = it.dailyUsedMillis,
                    sessionUsedMillis = it.sessionUsedMillis,
                    pendingOfflineGapMillis = it.pendingOfflineGapMillis
                )
            },
            eventStats = events.toEventStats()
        )
    }

    private fun List<EnforcementEventRecord>.toEventStats(): EventStats =
        EventStats(
            totalEvents = size,
            blockEvents = count { it.isBlockEvent() },
            cloneEvents = count {
                it.eventType == EnforcementEventType.CLONE_RESOLVED ||
                        it.eventType == EnforcementEventType.CLONE_UNRESOLVED ||
                        it.details["reasonCode"] == ReasonCode.CLONE_POLICY_BLOCK.name
            },
            permissionAbnormalEvents = count { it.isPermissionAbnormalEvent() },
            offlineGapEvents = count { it.eventType == EnforcementEventType.OFFLINE_GAP_DETECTED },
            offlineGapMillis = filter { it.eventType == EnforcementEventType.OFFLINE_GAP_DETECTED }
                .sumOf { it.details["durationMillis"]?.toLongOrNull() ?: 0L }
        )

    private fun EnforcementEventRecord.isBlockEvent(): Boolean {
        val reason = details["reasonCode"] ?: return false
        return reason.endsWith("_BLOCK") ||
                reason == ReasonCode.SYSTEM_HEALTH_FAIL_CLOSED.name ||
                reason == ReasonCode.PAGE_CONTEXT_MISSING.name
    }

    private fun EnforcementEventRecord.isPermissionAbnormalEvent(): Boolean =
        details["systemHealthIssues"]?.isNotBlank() == true ||
                details["overlayPermissionGranted"] == "false" ||
                details["accessibilityConnected"] == "false" ||
                details["foregroundServiceRunning"] == "false"

    private fun EnforcementEventRecord.dateKey(): String =
        Instant.ofEpochMilli(occurredAtMillis).atZone(zoneId).toLocalDate().toString()
}
