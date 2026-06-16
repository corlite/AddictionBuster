package com.addictionbuster

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
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

        return ScrollView(this).apply {
            addView(
                LinearLayout(this@StatsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(22), dp(26), dp(22), dp(18))
                    setBackgroundColor(Color.rgb(248, 250, 252))

                    addView(text("今日统计", 30, Color.rgb(15, 23, 42), bold = true), matchWrap())
                    addView(
                        text(snapshot.dateKey, 14, Color.rgb(100, 116, 139), bold = false).apply {
                            setPadding(0, dp(6), 0, dp(18))
                        },
                        matchWrap()
                    )

                    addView(sectionTitle("手机时长"), matchWrap())
                    addView(summaryLine("今日总时长", formatDuration(snapshot.phoneUsage.dailyUsedMillis)), matchWrap())
                    addView(summaryLine("本次解锁", formatDuration(snapshot.phoneUsage.sessionUsedMillis)), matchWrap())
                    addView(summaryLine("离线待确认", formatDuration(snapshot.phoneUsage.pendingOfflineGapMillis)), matchWrap())

                    addView(sectionTitle("事件"), spacedParams(top = 18))
                    addView(summaryLine("总事件", "${snapshot.eventStats.totalEvents} 次"), matchWrap())
                    addView(summaryLine("拦截事件", "${snapshot.eventStats.blockEvents} 次"), matchWrap())
                    addView(summaryLine("双开相关", "${snapshot.eventStats.cloneEvents} 次"), matchWrap())
                    addView(summaryLine("权限异常", "${snapshot.eventStats.permissionAbnormalEvents} 次"), matchWrap())
                    addView(
                        summaryLine(
                            "离线间隙",
                            "${snapshot.eventStats.offlineGapEvents} 次 / ${formatDuration(snapshot.eventStats.offlineGapMillis)}"
                        ),
                        matchWrap()
                    )

                    addView(sectionTitle("App 用量"), spacedParams(top = 18))
                    addAppUsageList(snapshot)
                },
                matchWrap()
            )
        }
    }

    private fun LinearLayout.addAppUsageList(snapshot: DailyStatsSnapshot) {
        if (snapshot.appUsages.isEmpty()) {
            addView(
                text("今天还没有 v2 App 使用记录。", 14, Color.rgb(100, 116, 139), bold = false).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, 0)
                },
                matchWrap()
            )
            return
        }

        snapshot.appUsages.forEach { usage ->
            addView(appUsageRow(usage), spacedParams(top = 8))
        }
    }

    private fun appUsageRow(usage: AppUsageStats): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            addView(text(appLabel(usage.identityKey), 16, Color.rgb(15, 23, 42), bold = true), matchWrap())
            addView(
                text(usage.identityKey, 12, Color.rgb(100, 116, 139), bold = false).apply {
                    setPadding(0, dp(2), 0, dp(6))
                },
                matchWrap()
            )
            addView(summaryLine("今日使用", formatDuration(usage.usedMillis)), matchWrap())
            addView(summaryLine("本次使用", formatDuration(usage.sessionUsedMillis)), matchWrap())
            addView(summaryLine("连续使用", formatDuration(usage.continuousUsedMillis)), matchWrap())
            addView(summaryLine("打开次数", "${usage.openCount} 次"), matchWrap())
            if (usage.pendingOfflineGapMillis > 0L) {
                addView(summaryLine("离线待确认", formatDuration(usage.pendingOfflineGapMillis)), matchWrap())
            }
        }

    private fun sectionTitle(value: String): TextView =
        text(value, 19, Color.rgb(15, 23, 42), bold = true)

    private fun summaryLine(label: String, value: String): TextView =
        text("$label：$value", 15, Color.rgb(51, 65, 85), bold = false).apply {
            setPadding(0, dp(4), 0, dp(4))
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

    private fun text(value: String, sp: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color)
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun spacedParams(top: Int): LinearLayout.LayoutParams =
        matchWrap().apply {
            setMargins(0, dp(top), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
