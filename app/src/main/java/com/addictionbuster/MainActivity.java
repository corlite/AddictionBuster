package com.addictionbuster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView serviceStatusView;
    private TextView mediaStatusView;
    private TextView selectedCountView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "main", "main screen created");
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(16));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text(getString(R.string.app_name), 30, Color.rgb(15, 23, 42), true);
        root.addView(title);

        TextView subtitle = text("选择想戒断的应用。打开它们时，会先进入 15 秒呼吸延迟，完成后本次放行。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        serviceStatusView = text("", 14, Color.rgb(30, 64, 175), false);
        root.addView(serviceStatusView);

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("开启无障碍拦截服务");
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton, matchWrap());

        mediaStatusView = text("", 14, Color.rgb(30, 64, 175), false);
        mediaStatusView.setPadding(0, dp(8), 0, 0);
        root.addView(mediaStatusView);

        Button notificationAccessButton = new Button(this);
        notificationAccessButton.setText("开启后台媒体阻断");
        notificationAccessButton.setAllCaps(false);
        notificationAccessButton.setOnClickListener(v -> startActivity(new Intent(this, NotificationAccessGuideActivity.class)));
        root.addView(notificationAccessButton, matchWrap());

        Button diagnosticButton = new Button(this);
        diagnosticButton.setText("查看诊断日志");
        diagnosticButton.setAllCaps(false);
        diagnosticButton.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticActivity.class)));
        root.addView(diagnosticButton, matchWrap());

        selectedCountView = text("", 14, Color.rgb(51, 65, 85), false);
        selectedCountView.setPadding(0, dp(14), 0, dp(8));
        root.addView(selectedCountView);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(appList);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        populateAppList(appList);
        return root;
    }

    private void populateAppList(LinearLayout appList) {
        Set<String> selected = RuleStore.getBlockedPackages(this);
        List<AppInfo> apps = loadLaunchableApps();

        for (AppInfo app : apps) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(app.label + "\n" + app.packageName);
            checkBox.setTextSize(15);
            checkBox.setTextColor(Color.rgb(15, 23, 42));
            checkBox.setPadding(0, dp(8), 0, dp(8));
            checkBox.setChecked(selected.contains(app.packageName));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Set<String> current = RuleStore.getBlockedPackages(this);
                if (isChecked) {
                    current.add(app.packageName);
                } else {
                    current.remove(app.packageName);
                }
                RuleStore.saveBlockedPackages(this, current);
                DiagnosticLogger.log(this, "rule", (isChecked ? "blocked " : "unblocked ") + app.packageName + " label=" + app.label);
                updateSelectedCount();
            });
            appList.addView(checkBox, matchWrap());
        }

        DiagnosticLogger.log(this, "main", "loaded launchable apps=" + apps.size() + " selected=" + selected.size());
        updateSelectedCount();
    }

    private List<AppInfo> loadLaunchableApps() {
        PackageManager packageManager = getPackageManager();
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= 33) {
            resolved = packageManager.queryIntentActivities(
                    launchIntent,
                    PackageManager.ResolveInfoFlags.of(0)
            );
        } else {
            resolved = packageManager.queryIntentActivities(launchIntent, 0);
        }

        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            apps.add(new AppInfo(label == null ? packageName : label.toString(), packageName));
        }

        Collator collator = Collator.getInstance(Locale.CHINA);
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    private void updateStatus() {
        if (serviceStatusView == null) {
            return;
        }
        boolean enabled = isAccessibilityServiceEnabled();
        serviceStatusView.setText(enabled ? "状态：拦截服务已开启" : "状态：还没有开启无障碍服务");
        serviceStatusView.setTextColor(enabled ? Color.rgb(22, 101, 52) : Color.rgb(185, 28, 28));
        boolean mediaEnabled = isNotificationListenerEnabled();
        if (mediaStatusView != null) {
            mediaStatusView.setText(mediaEnabled ? "状态：后台媒体阻断已开启" : "状态：还没有开启通知访问，后台播放可能无法阻断");
            mediaStatusView.setTextColor(mediaEnabled ? Color.rgb(22, 101, 52) : Color.rgb(185, 28, 28));
        }
        DiagnosticLogger.log(this, "main", "onResume serviceEnabled=" + enabled + " mediaEnabled=" + mediaEnabled + " selected=" + RuleStore.getBlockedPackages(this).size());
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        if (selectedCountView == null) {
            return;
        }
        int count = RuleStore.getBlockedPackages(this).size();
        selectedCountView.setText("已选择 " + count + " 个要限制的应用");
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, BusterAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }
        String expectedName = expected.flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (expectedName.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        ComponentName expected = new ComponentName(this, BusterNotificationListenerService.class);
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabledListeners == null) {
            return false;
        }
        String expectedName = expected.flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledListeners);
        while (splitter.hasNext()) {
            if (expectedName.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
