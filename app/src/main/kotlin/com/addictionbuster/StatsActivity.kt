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
import com.addictionbuster.enforcement.runtime.V2AccessibilityRuntime
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository
import com.addictionbuster.enforcement.storage.LocalEventStore
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository

class StatsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLogger.log(this, "stats", "stats screen opened")
        setContentView(buildContent())
        MascotSoundPlayer.play(this, MascotVoiceSlot.TODAY_REPORT)
    }

    private fun buildContent(): ScrollView {
        if (V2RuntimeMode.isEnabled(this)) {
            V2AccessibilityRuntime.flushForStatsReport()
        }
        val snapshot = EnforcementStatsAggregator(
            appUsageRepository = LocalAppUsageRepository(this),
            phoneUsageRepository = LocalPhoneUsageRepository(this),
            eventStore = LocalEventStore(this)
        ).dailySnapshot()

        val root = UiKit.screen(this).apply {
            addView(UiKit.title(this@StatsActivity, getString(R.string.stats_title)), UiKit.matchWrap())
            addView(UiKit.subtitle(this@StatsActivity, snapshot.dateKey), UiKit.matchWrap())

            addView(summaryCard(snapshot), UiKit.matchWrap())
            addView(eventCard(snapshot), UiKit.spaced(this@StatsActivity, 12))
            addView(appUsageCard(snapshot), UiKit.spaced(this@StatsActivity, 12))
        }
        return UiKit.scrollScreen(this, root)
    }

    private fun summaryCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, getString(R.string.section_summary)), UiKit.matchWrap())
            UiKit.addInfoRow(this, getString(R.string.label_phone_usage), formatDuration(snapshot.phoneUsage.dailyUsedMillis))
            UiKit.addInfoRow(this, getString(R.string.label_current_unlock), formatDuration(snapshot.phoneUsage.sessionUsedMillis))
            UiKit.addInfoRow(this, getString(R.string.label_interceptions), getString(R.string.count_events_format, snapshot.eventStats.blockEvents))
            UiKit.addInfoRow(this, getString(R.string.label_pending_offline), formatDuration(snapshot.phoneUsage.pendingOfflineGapMillis))
        }

    private fun eventCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, getString(R.string.section_event_details)), UiKit.matchWrap())
            UiKit.addInfoRow(this, getString(R.string.label_total_events), getString(R.string.count_events_format, snapshot.eventStats.totalEvents))
            UiKit.addInfoRow(this, getString(R.string.label_clone_events), getString(R.string.count_events_format, snapshot.eventStats.cloneEvents))
            UiKit.addInfoRow(this, getString(R.string.label_permission_events), getString(R.string.count_events_format, snapshot.eventStats.permissionAbnormalEvents))
            UiKit.addInfoRow(
                this,
                getString(R.string.label_offline_gap),
                getString(R.string.stats_offline_gap_format, snapshot.eventStats.offlineGapEvents, formatDuration(snapshot.eventStats.offlineGapMillis))
            )
        }

    private fun appUsageCard(snapshot: DailyStatsSnapshot): LinearLayout =
        UiKit.card(this).apply {
            addView(UiKit.sectionTitle(this@StatsActivity, getString(R.string.section_app_usage)), UiKit.matchWrap())
            addAppUsageList(snapshot)
        }

    private fun LinearLayout.addAppUsageList(snapshot: DailyStatsSnapshot) {
        if (snapshot.appUsages.isEmpty()) {
            addView(
                MascotUi.emptyState(
                    this@StatsActivity,
                    getString(R.string.stats_empty)
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
            addView(summaryLine(getString(R.string.label_daily_usage), formatDuration(usage.usedMillis)), UiKit.matchWrap())
            addView(summaryLine(getString(R.string.label_session_usage), formatDuration(usage.sessionUsedMillis)), UiKit.matchWrap())
            addView(summaryLine(getString(R.string.label_continuous_usage), formatDuration(usage.continuousUsedMillis)), UiKit.matchWrap())
            addView(summaryLine(getString(R.string.label_open_count), getString(R.string.count_events_format, usage.openCount)), UiKit.matchWrap())
            if (usage.pendingOfflineGapMillis > 0L) {
                addView(summaryLine(getString(R.string.label_pending_offline), formatDuration(usage.pendingOfflineGapMillis)), UiKit.matchWrap())
            }
        }

    private fun summaryLine(label: String, value: String): TextView =
        UiKit.body(this, getString(R.string.summary_line_format, label, value)).apply {
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
            hours > 0L -> getString(R.string.duration_hours_minutes_format, hours, minutes)
            minutes > 0L -> getString(R.string.duration_minutes_seconds_format, minutes, seconds)
            else -> getString(R.string.duration_seconds_format, seconds)
        }
    }

}
