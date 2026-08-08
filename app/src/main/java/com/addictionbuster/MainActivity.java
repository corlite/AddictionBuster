package com.addictionbuster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.addictionbuster.bootstrap.V2InitializationGate;
import com.addictionbuster.bootstrap.V2RequiredSetupActivity;
import com.addictionbuster.enforcement.stats.DailyStatsSnapshot;
import com.addictionbuster.enforcement.stats.EnforcementStatsAggregator;
import com.addictionbuster.enforcement.storage.LocalAppUsageRepository;
import com.addictionbuster.enforcement.storage.LocalEventStore;
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository;

import java.time.LocalDate;
import java.time.ZoneId;

public class MainActivity extends Activity {
    private TextView selectedCountView;
    private TextView phoneUsageView;
    private TextView eventCountView;
    private TextView serviceStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "main", "home screen created");
        if (V2RuntimeMode.isEnabled(this)) {
            V2EnforcementForegroundService.start(this);
        }
        if (V2InitializationGate.requiresSetup(this)) {
            startActivity(new Intent(this, V2RequiredSetupActivity.class));
        }
        setContentView(buildContent());
        MascotSoundPlayer.play(this, MascotVoiceSlot.CONTROL_APPS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSelectedCount();
        DiagnosticLogger.log(this, "main", "home onResume selected=" + RuleStore.getBlockedPackages(this).size());
    }

    private ScrollView buildContent() {
        LinearLayout root = UiKit.screen(this);

        root.addView(UiKit.title(this, getString(R.string.app_name)), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, getString(R.string.main_subtitle)), UiKit.matchWrap());

        LinearLayout statusCard = UiKit.card(this);
        statusCard.addView(UiKit.sectionTitle(this, getString(R.string.section_today_status)), UiKit.matchWrap());
        statusCard.addView(MascotUi.compactStatus(this), UiKit.matchWrap());
        selectedCountView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        phoneUsageView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        eventCountView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        serviceStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(statusCard, getString(R.string.status_controlled_apps), selectedCountView);
        addStatusRow(statusCard, getString(R.string.status_phone_usage_today), phoneUsageView);
        addStatusRow(statusCard, getString(R.string.status_block_events_today), eventCountView);
        addStatusRow(statusCard, getString(R.string.status_service), serviceStatusView);
        root.addView(statusCard, UiKit.matchWrap());

        root.addView(UiKit.sectionTitle(this, getString(R.string.section_controlled_apps)), UiKit.spaced(this, 20));

        Button activeAppsButton = UiKit.entryButton(
                this,
                getString(R.string.action_active_apps),
                getString(R.string.action_active_apps_subtitle)
        );
        activeAppsButton.setOnClickListener(v -> startActivity(new Intent(this, ActiveAppsActivity.class)));
        root.addView(activeAppsButton, UiKit.matchWrap());

        Button addAppsButton = UiKit.entryButton(
                this,
                getString(R.string.action_add_apps),
                getString(R.string.action_add_apps_subtitle)
        );
        addAppsButton.setOnClickListener(v -> startActivity(new Intent(this, AddAppActivity.class)));
        root.addView(addAppsButton, UiKit.spaced(this, 10));

        root.addView(UiKit.sectionTitle(this, getString(R.string.section_time_reports)), UiKit.spaced(this, 20));

        Button phoneLimitButton = UiKit.entryButton(
                this,
                getString(R.string.action_phone_limit),
                getString(R.string.action_phone_limit_subtitle)
        );
        phoneLimitButton.setOnClickListener(v -> startActivity(new Intent(this, PhoneLimitActivity.class)));
        root.addView(phoneLimitButton, UiKit.matchWrap());

        Button statsButton = UiKit.entryButton(
                this,
                getString(R.string.action_today_report),
                getString(R.string.action_today_report_subtitle)
        );
        statsButton.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        root.addView(statsButton, UiKit.spaced(this, 10));

        root.addView(UiKit.sectionTitle(this, getString(R.string.section_system)), UiKit.spaced(this, 20));

        Button settingsButton = UiKit.entryButton(
                this,
                getString(R.string.action_settings),
                getString(R.string.action_settings_subtitle)
        );
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, AppSettingsActivity.class)));
        root.addView(settingsButton, UiKit.matchWrap());

        TextView hint = UiKit.hint(this, getString(R.string.main_permission_hint));
        hint.setPadding(0, UiKit.dp(this, 18), 0, 0);
        root.addView(hint, UiKit.matchWrap());

        updateSelectedCount();
        return UiKit.scrollScreen(this, root);
    }

    private void addStatusRow(LinearLayout parent, String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 5));

        TextView labelView = UiKit.text(this, label, 14, UiKit.COLOR_MUTED, false);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        valueView.setTextColor(UiKit.COLOR_TEXT);
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        parent.addView(row, UiKit.matchWrap());
    }

    private void updateSelectedCount() {
        if (selectedCountView == null || phoneUsageView == null || eventCountView == null || serviceStatusView == null) {
            return;
        }
        int count = RuleStore.getBlockedPackages(this).size();
        selectedCountView.setText(getString(R.string.count_apps_format, count));

        try {
            DailyStatsSnapshot snapshot = new EnforcementStatsAggregator(
                    new LocalAppUsageRepository(this),
                    new LocalPhoneUsageRepository(this),
                    new LocalEventStore(this),
                    ZoneId.systemDefault()
            ).dailySnapshot(LocalDate.now(ZoneId.systemDefault()).toString());
            phoneUsageView.setText(formatDuration(snapshot.getPhoneUsage().getDailyUsedMillis()));
            eventCountView.setText(getString(R.string.count_events_format, snapshot.getEventStats().getBlockEvents()));
        } catch (RuntimeException exception) {
            phoneUsageView.setText(R.string.status_unavailable);
            eventCountView.setText(R.string.status_unavailable);
        }

        if (V2InitializationGate.requiresSetup(this)) {
            serviceStatusView.setText(R.string.service_requires_setup);
            serviceStatusView.setTextColor(UiKit.COLOR_DANGER);
        } else if (isAccessibilityServiceEnabled()) {
            serviceStatusView.setText(R.string.service_ok);
            serviceStatusView.setTextColor(UiKit.COLOR_SUCCESS);
        } else {
            serviceStatusView.setText(R.string.service_requires_accessibility);
            serviceStatusView.setTextColor(UiKit.COLOR_DANGER);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, BusterAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return containsFlattenedComponent(enabledServices, expected);
    }

    private boolean containsFlattenedComponent(String values, ComponentName expected) {
        if (values == null) {
            return false;
        }
        String expectedName = expected.flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(values);
        while (splitter.hasNext()) {
            if (expectedName.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return getString(R.string.duration_hours_minutes_format, hours, minutes);
        }
        if (minutes > 0L) {
            return getString(R.string.duration_minutes_seconds_format, minutes, seconds);
        }
        return getString(R.string.duration_seconds_format, seconds);
    }
}
