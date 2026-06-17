package com.addictionbuster

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.addictionbuster.enforcement.stats.AppUsageStats
import com.addictionbuster.enforcement.stats.DailyStatsSnapshot
import com.addictionbuster.enforcement.stats.EnforcementStatsAggregator
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository

class StatsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLogger.log(this, "stats", "stats screen opened")
        setContentView(buildContent())
    }

    private fun buildContent(): ScrollView {
        val snapshot = EnforcementStatsAggregator(
            appUsageRepository = LocalAppUsageRepository(this),
            phoneUsageRepository = LocalPhoneUsageRepository(this),
            eventStore = LocalEventStore(this)
        ).dailySnapshot()

        val root = UiKit.screen(this).apply {
            addView(UiKit.title(this@StatsActivity, "今日报告"), UiKit.matchWrap())
            addView(UiKit.subtitle(this@StatsActivity, snapshot.dateKey), UiKit.matchWrap())

            addView(summaryCard(snapshot), UiKit.matchWrap())
            addView(eventCard(snapshot), UiKit.spaced(this@StatsActivity, 12))
            addView(appUsageCard(snapshot), UiKit.spaced(this@StatsActivity, 12))
        }
        return UiKit.scrollScreen(this, root)
    }

    private fun summaryCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, "总览"), UiKit.matchWrap())
            UiKit.addInfoRow(this, "手机使用", formatDuration(snapshot.phoneUsage.dailyUsedMillis))
            UiKit.addInfoRow(this, "本次解锁", formatDuration(snapshot.phoneUsage.sessionUsedMillis))
            UiKit.addInfoRow(this, "拦截事件", "${snapshot.eventStats.blockEvents} 次")
            UiKit.addInfoRow(this, "离线待确认", formatDuration(snapshot.phoneUsage.pendingOfflineGapMillis))
        }

    private fun eventCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, "事件明细"), UiKit.matchWrap())
            UiKit.addInfoRow(this, "总事件", "${snapshot.eventStats.totalEvents} 次")
            UiKit.addInfoRow(this, "双开相关", "${snapshot.eventStats.cloneEvents} 次")
            UiKit.addInfoRow(this, "权限异常", "${snapshot.eventStats.permissionAbnormalEvents} 次")
            UiKit.addInfoRow(
                this,
                "离线间隙",
                "${snapshot.eventStats.offlineGapEvents} 次 / ${formatDuration(snapshot.eventStats.offlineGapMillis)}"
            )
        }

    private fun appUsageCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, "App 用量"), UiKit.matchWrap())
            addAppUsageList(snapshot)
        }

    private fun LinearLayout.addAppUsageList(snapshot: DailyStatsSnapshot) {
        if (snapshot.appUsages.isEmpty()) {
            addView(
                MascotUi.emptyState(
                    this@StatsActivity,
                    "今天还没有统计记录。打开受控应用或设置手机时长后，这里会显示今日报告。"
                ),
                UiKit.matchWrap()
            )
            return
        }

        snapshot.appUsages.forEach { usage ->
            addView(appUsageRow(usage), UiKit.spaced(this@StatsActivity, 8))
        }
    }

    private fun appUsageRow(usage: AppUsageStats): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(this@StatsActivity, 8), 0, UiKit.dp(this@StatsActivity, 8))

            addView(UiKit.text(this@StatsActivity, appLabel(usage.identityKey), 16, UiKit.COLOR_TEXT, true), UiKit.matchWrap())
            addView(
                UiKit.hint(this@StatsActivity, usage.identityKey).apply {
                    setPadding(0, UiKit.dp(this@StatsActivity, 2), 0, UiKit.dp(this@StatsActivity, 6))
                },
                UiKit.matchWrap()
            )
            addView(summaryLine("今日使用", formatDuration(usage.usedMillis)), UiKit.matchWrap())
            addView(summaryLine("本次使用", formatDuration(usage.sessionUsedMillis)), UiKit.matchWrap())
            addView(summaryLine("连续使用", formatDuration(usage.continuousUsedMillis)), UiKit.matchWrap())
            addView(summaryLine("打开次数", "${usage.openCount} 次"), UiKit.matchWrap())
            if (usage.pendingOfflineGapMillis > 0L) {
                addView(summaryLine("离线待确认", formatDuration(usage.pendingOfflineGapMillis)), UiKit.matchWrap())
            }
        }

    private fun summaryLine(label: String, value: String): TextView =
        UiKit.body(this, "$label：$value").apply {
            setPadding(0, UiKit.dp(this@StatsActivity, 3), 0, UiKit.dp(this@StatsActivity, 3))
        }

    private fun appLabel(identityKey: String): String =
        AppCatalog.loadLabel(this, identityKey)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "${hours}小时${minutes}分钟"
            minutes > 0L -> "${minutes}分钟${seconds}秒"
            else -> "${seconds}秒"
        }
    }

}
