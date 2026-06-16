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
        root.addView(UiKit.subtitle(this, "今日先挡住入口，别急着靠意志力硬扛。"), UiKit.matchWrap());

        LinearLayout statusCard = UiKit.card(this);
        statusCard.addView(UiKit.sectionTitle(this, "今日状态"), UiKit.matchWrap());
        selectedCountView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        phoneUsageView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        eventCountView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        serviceStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(statusCard, "已管控应用", selectedCountView);
        addStatusRow(statusCard, "今日手机时长", phoneUsageView);
        addStatusRow(statusCard, "今日拦截事件", eventCountView);
        addStatusRow(statusCard, "服务状态", serviceStatusView);
        root.addView(statusCard, UiKit.matchWrap());

        root.addView(UiKit.sectionTitle(this, "管控应用"), UiKit.spaced(this, 20));

        Button activeAppsButton = UiKit.entryButton(this, "已管控应用", "查看和修改已经启用的应用");
        activeAppsButton.setOnClickListener(v -> startActivity(new Intent(this, ActiveAppsActivity.class)));
        root.addView(activeAppsButton, UiKit.matchWrap());

        Button addAppsButton = UiKit.entryButton(this, "添加应用", "搜索并添加新的管控应用");
        addAppsButton.setOnClickListener(v -> startActivity(new Intent(this, AddAppActivity.class)));
        root.addView(addAppsButton, UiKit.spaced(this, 10));

        root.addView(UiKit.sectionTitle(this, "时长与报告"), UiKit.spaced(this, 20));

        Button phoneLimitButton = UiKit.entryButton(this, "手机时长限制", "设置每日总时长、单次打开手机时长和白名单");
        phoneLimitButton.setOnClickListener(v -> startActivity(new Intent(this, PhoneLimitActivity.class)));
        root.addView(phoneLimitButton, UiKit.matchWrap());

        Button statsButton = UiKit.entryButton(this, "今日报告", "查看今日时长、拦截事件和 App 用量");
        statsButton.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        root.addView(statsButton, UiKit.spaced(this, 10));

        root.addView(UiKit.sectionTitle(this, "系统"), UiKit.spaced(this, 20));

        Button settingsButton = UiKit.entryButton(this, "设置", "权限、诊断和后台媒体");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, AppSettingsActivity.class)));
        root.addView(settingsButton, UiKit.matchWrap());

        TextView hint = UiKit.hint(this, "前台拦截依赖无障碍服务；后台媒体阻断需要通知使用权。");
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
        selectedCountView.setText(count + " 个");

        try {
            DailyStatsSnapshot snapshot = new EnforcementStatsAggregator(
                    new LocalAppUsageRepository(this),
                    new LocalPhoneUsageRepository(this),
                    new LocalEventStore(this),
                    ZoneId.systemDefault()
            ).dailySnapshot(LocalDate.now(ZoneId.systemDefault()).toString());
            phoneUsageView.setText(formatDuration(snapshot.getPhoneUsage().getDailyUsedMillis()));
            eventCountView.setText(snapshot.getEventStats().getBlockEvents() + " 次");
        } catch (RuntimeException exception) {
            phoneUsageView.setText("暂不可用");
            eventCountView.setText("暂不可用");
        }

        if (V2InitializationGate.requiresSetup(this)) {
            serviceStatusView.setText("需初始化");
            serviceStatusView.setTextColor(UiKit.COLOR_DANGER);
        } else if (isAccessibilityServiceEnabled()) {
            serviceStatusView.setText("正常");
            serviceStatusView.setTextColor(UiKit.COLOR_SUCCESS);
        } else {
            serviceStatusView.setText("需开启无障碍");
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
            return hours + "小时" + minutes + "分钟";
        }
        if (minutes > 0L) {
            return minutes + "分钟" + seconds + "秒";
        }
        return seconds + "秒";
    }
}
