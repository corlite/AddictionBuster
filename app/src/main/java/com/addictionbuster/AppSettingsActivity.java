package com.addictionbuster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppSettingsActivity extends Activity {
    private TextView accessibilityStatusView;
    private TextView mediaStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "settings", "settings screen opened");
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
        root.setPadding(dp(22), dp(26), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("设置", 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text("权限、后台媒体阻断和诊断日志都在这里。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        accessibilityStatusView = text("", 14, Color.rgb(30, 64, 175), false);
        root.addView(accessibilityStatusView, matchWrap());

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("开启无障碍拦截服务");
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton, matchWrap());

        mediaStatusView = text("", 14, Color.rgb(30, 64, 175), false);
        mediaStatusView.setPadding(0, dp(14), 0, 0);
        root.addView(mediaStatusView, matchWrap());

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

        updateStatus();
        return root;
    }

    private void updateStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        if (accessibilityStatusView != null) {
            accessibilityStatusView.setText(accessibilityEnabled ? "无障碍拦截：已开启" : "无障碍拦截：未开启");
            accessibilityStatusView.setTextColor(accessibilityEnabled ? Color.rgb(22, 101, 52) : Color.rgb(185, 28, 28));
        }

        boolean mediaEnabled = isNotificationListenerEnabled();
        if (mediaStatusView != null) {
            mediaStatusView.setText(mediaEnabled ? "后台媒体阻断：已开启" : "后台媒体阻断：未开启");
            mediaStatusView.setTextColor(mediaEnabled ? Color.rgb(22, 101, 52) : Color.rgb(185, 28, 28));
        }

        DiagnosticLogger.log(this, "settings", "status accessibilityEnabled=" + accessibilityEnabled + " mediaEnabled=" + mediaEnabled);
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
