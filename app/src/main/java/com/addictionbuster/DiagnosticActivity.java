package com.addictionbuster;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiagnosticActivity extends Activity {
    private static final int DEFAULT_LOG_WINDOW_MINUTES = 60;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView logWindowView;
    private TextView logView;
    private int logWindowMinutes = DEFAULT_LOG_WINDOW_MINUTES;

    private final Runnable pruneTick = new Runnable() {
        @Override
        public void run() {
            DiagnosticLogger.pruneOldEntries(DiagnosticActivity.this);
            refreshLog();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.pruneOldEntries(this);
        DiagnosticLogger.log(this, "diagnostic", "diagnostic center opened");
        setContentView(buildContent());
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        handler.removeCallbacks(pruneTick);
        handler.postDelayed(pruneTick, 60_000L);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(pruneTick);
        super.onPause();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("诊断中心", 26, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView hint = text("复现问题后，点“发送诊断给开发者”。诊断会包含版本、权限状态、最近关键事件和最近 1 小时日志。", 14, Color.rgb(71, 85, 105), false);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint, matchWrap());

        statusView = text("", 14, Color.rgb(15, 23, 42), false);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(statusView, matchWrap());

        Button shareButton = button("发送诊断给开发者");
        shareButton.setTextSize(17);
        shareButton.setOnClickListener(v -> shareDiagnosticReport());
        root.addView(shareButton, matchWrap());

        Button refreshButton = button("刷新诊断");
        refreshButton.setOnClickListener(v -> refreshAll());
        root.addView(refreshButton, matchWrap());

        TextView advancedTitle = text("高级日志", 18, Color.rgb(30, 64, 175), true);
        advancedTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(advancedTitle, matchWrap());

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        Button last30Button = button("最近 30 分钟");
        last30Button.setOnClickListener(v -> setLogWindow(30));
        filterRow.addView(last30Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button last60Button = button("最近 1 小时");
        last60Button.setOnClickListener(v -> setLogWindow(60));
        filterRow.addView(last60Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(filterRow, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button copyButton = button("复制诊断");
        copyButton.setOnClickListener(v -> copyDiagnosticReport());
        actionRow.addView(copyButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clearButton = button("清空日志");
        clearButton.setOnClickListener(v -> clearLog());
        actionRow.addView(clearButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actionRow, matchWrap());

        logWindowView = text("", 13, Color.rgb(100, 116, 139), false);
        logWindowView.setGravity(Gravity.CENTER);
        logWindowView.setPadding(0, dp(6), 0, 0);
        root.addView(logWindowView, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        logView = text("", 12, Color.rgb(15, 23, 42), false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(10), 0, dp(10));
        scrollView.addView(logView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private void refreshAll() {
        DiagnosticLogger.pruneOldEntries(this);
        refreshStatus();
        refreshLog();
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }

        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean mediaEnabled = isNotificationListenerEnabled();
        int blockedCount = RuleStore.getBlockedPackages(this).size();
        String latestLine = DiagnosticLogger.lastImportantLine(this);

        statusView.setText(
                "版本：" + versionName() + "\n"
                        + "无障碍拦截：" + (accessibilityEnabled ? "已开启" : "未开启") + "\n"
                        + "后台媒体阻断：" + (mediaEnabled ? "已开启" : "未开启") + "\n"
                        + "生效应用：" + blockedCount + " 个\n"
                        + "日志保留：自动保留最近 1 小时，每分钟检查一次\n"
                        + "最近关键事件：" + latestLine
        );
    }

    private void refreshLog() {
        if (logWindowView != null) {
            logWindowView.setText("当前显示最近 " + logWindowMinutes + " 分钟日志");
        }
        if (logView != null) {
            logView.setText(DiagnosticLogger.readRecent(this, logWindowMinutes));
        }
    }

    private void setLogWindow(int minutes) {
        logWindowMinutes = minutes;
        refreshLog();
    }

    private void copyDiagnosticReport() {
        String report = buildDiagnosticReport();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AddictionBuster diagnostic report", report));
            Toast.makeText(this, "诊断报告已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareDiagnosticReport() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "瘾头破坏器诊断报告");
        send.putExtra(Intent.EXTRA_TEXT, buildDiagnosticReport());
        startActivity(Intent.createChooser(send, "发送诊断给开发者"));
    }

    private String buildDiagnosticReport() {
        return "瘾头破坏器诊断报告\n"
                + "生成时间：" + nowText() + "\n"
                + "版本：" + versionName() + "\n"
                + "设备：" + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Android：" + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + "无障碍拦截：" + (isAccessibilityServiceEnabled() ? "已开启" : "未开启") + "\n"
                + "后台媒体阻断：" + (isNotificationListenerEnabled() ? "已开启" : "未开启") + "\n"
                + "生效应用：" + RuleStore.getBlockedPackages(this).size() + " 个\n"
                + "最近关键事件：" + DiagnosticLogger.lastImportantLine(this) + "\n\n"
                + "提示：日志可能包含应用名称和包名，用于判断拦截命中的目标。\n\n"
                + "最近 1 小时日志：\n"
                + DiagnosticLogger.readRecent(this, 60);
    }

    private void clearLog() {
        DiagnosticLogger.clear(this);
        refreshAll();
        Toast.makeText(this, "诊断日志已清空", Toast.LENGTH_SHORT).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, BusterAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return containsFlattenedComponent(enabledServices, expected);
    }

    private boolean isNotificationListenerEnabled() {
        ComponentName expected = new ComponentName(this, BusterNotificationListenerService.class);
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        return containsFlattenedComponent(enabledListeners, expected);
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

    private String versionName() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = packageManager.getPackageInfo(
                        getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = packageManager.getPackageInfo(getPackageName(), 0);
            }
            return info.versionName == null ? "未知" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知";
        }
    }

    private String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
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
