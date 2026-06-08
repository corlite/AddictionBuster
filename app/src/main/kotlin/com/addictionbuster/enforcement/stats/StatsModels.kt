package com.addictionbuster.enforcement.stats

data class DailyStatsSnapshot(
    val dateKey: String,
    val appUsages: List<AppUsageStats>,
    val phoneUsage: PhoneUsageStats,
    val eventStats: EventStats
)

data class AppUsageStats(
    val identityKey: String,
    val usedMillis: Long,
    val sessionUsedMillis: Long,
    val continuousUsedMillis: Long,
    val openCount: Int,
    val pendingOfflineGapMillis: Long
)

data class PhoneUsageStats(
    val dailyUsedMillis: Long,
    val sessionUsedMillis: Long,
    val pendingOfflineGapMillis: Long
)

data class EventStats(
    val totalEvents: Int,
    val blockEvents: Int,
    val cloneEvents: Int,
    val permissionAbnormalEvents: Int,
    val offlineGapEvents: Int,
    val offlineGapMillis: Long
)
