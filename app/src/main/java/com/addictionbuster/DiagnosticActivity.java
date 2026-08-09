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

        TextView title = text(getString(R.string.diagnostic_title), 26, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView hint = text(getString(R.string.diagnostic_intro), 14, Color.rgb(71, 85, 105), false);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint, matchWrap());

        statusView = text("", 14, Color.rgb(15, 23, 42), false);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(statusView, matchWrap());

        Button shareButton = button(getString(R.string.diagnostic_share));
        shareButton.setTextSize(17);
        shareButton.setOnClickListener(v -> shareDiagnosticReport());
        root.addView(shareButton, matchWrap());

        Button refreshButton = button(getString(R.string.diagnostic_refresh));
        refreshButton.setOnClickListener(v -> refreshAll());
        root.addView(refreshButton, matchWrap());

        TextView advancedTitle = text(getString(R.string.diagnostic_advanced_logs), 18, Color.rgb(30, 64, 175), true);
        advancedTitle.setPadding(0, dp(12), 0, dp(6));
        root.addView(advancedTitle, matchWrap());

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        Button last30Button = button(getString(R.string.diagnostic_last_30));
        last30Button.setOnClickListener(v -> setLogWindow(30));
        filterRow.addView(last30Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button last60Button = button(getString(R.string.diagnostic_last_60));
        last60Button.setOnClickListener(v -> setLogWindow(60));
        filterRow.addView(last60Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(filterRow, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button copyButton = button(getString(R.string.diagnostic_copy));
        copyButton.setOnClickListener(v -> copyDiagnosticReport());
        actionRow.addView(copyButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clearButton = button(getString(R.string.diagnostic_clear));
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
        int phoneWhitelistCount = RuleStore.getPhoneWhitelistPackages(this).size();
        int phoneDailyLimit = RuleStore.getPhoneDailyLimitMinutes(this);
        int phoneSessionLimit = RuleStore.getPhoneSessionLimitMinutes(this);
        long phoneUsedMinutes = V2RuleBridge.getPhoneDailyUsedMinutes(this);
        String latestLine = DiagnosticLogger.lastImportantLine(this);

        statusView.setText(getString(
                R.string.diagnostic_status_format,
                versionName(),
                getString(accessibilityEnabled ? R.string.status_enabled : R.string.status_disabled),
                getString(mediaEnabled ? R.string.status_enabled : R.string.status_disabled),
                blockedCount,
                limitText(phoneDailyLimit),
                limitText(phoneSessionLimit),
                phoneUsedMinutes,
                phoneWhitelistCount,
                latestLine
        ));
    }

    private void refreshLog() {
        if (logWindowView != null) {
            logWindowView.setText(getString(R.string.diagnostic_log_window_format, logWindowMinutes));
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
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostic_share_subject), report));
            Toast.makeText(this, R.string.diagnostic_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareDiagnosticReport() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostic_share_subject));
        send.putExtra(Intent.EXTRA_TEXT, buildDiagnosticReport());
        startActivity(Intent.createChooser(send, getString(R.string.diagnostic_share_chooser)));
    }

    private String buildDiagnosticReport() {
        return getString(
                R.string.diagnostic_report_format,
                nowText(),
                versionName(),
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                getString(isAccessibilityServiceEnabled() ? R.string.status_enabled : R.string.status_disabled),
                getString(isNotificationListenerEnabled() ? R.string.status_enabled : R.string.status_disabled),
                RuleStore.getBlockedPackages(this).size(),
                limitText(RuleStore.getPhoneDailyLimitMinutes(this)),
                limitText(RuleStore.getPhoneSessionLimitMinutes(this)),
                V2RuleBridge.getPhoneDailyUsedMinutes(this),
                RuleStore.getPhoneWhitelistPackages(this).size(),
                DiagnosticLogger.lastImportantLine(this),
                DiagnosticLogger.readRecent(this, 60)
        );
    }

    private String limitText(int minutes) {
        return minutes <= 0
                ? getString(R.string.diagnostic_limit_disabled)
                : getString(R.string.diagnostic_limit_minutes, minutes);
    }

    private void clearLog() {
        DiagnosticLogger.clear(this);
        refreshAll();
        Toast.makeText(this, R.string.diagnostic_cleared, Toast.LENGTH_SHORT).show();
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
            return info.versionName == null ? getString(R.string.unknown) : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return getString(R.string.unknown);
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
