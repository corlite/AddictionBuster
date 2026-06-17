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

    private ScrollView buildContent() {
        LinearLayout root = UiKit.screen(this);

        root.addView(UiKit.title(this, "设置"), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, "权限、后台媒体阻断和诊断日志都在这里。"), UiKit.matchWrap());

        LinearLayout requiredCard = UiKit.card(this);
        requiredCard.addView(UiKit.sectionTitle(this, "必要权限"), UiKit.matchWrap());
        accessibilityStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(requiredCard, "无障碍拦截服务", accessibilityStatusView);
        Button accessibilityButton = UiKit.entryButton(this, "开启无障碍拦截服务", "用于识别前台 App 并显示拦截层");
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        requiredCard.addView(accessibilityButton, UiKit.spaced(this, 10));
        root.addView(requiredCard, UiKit.matchWrap());

        LinearLayout optionalCard = UiKit.card(this);
        optionalCard.addView(UiKit.sectionTitle(this, "可选能力"), UiKit.matchWrap());
        mediaStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(optionalCard, "后台媒体阻断", mediaStatusView);
        Button notificationAccessButton = UiKit.entryButton(this, "开启后台媒体阻断", "尝试暂停受控应用的后台播放声音");
        notificationAccessButton.setOnClickListener(v -> startActivity(new Intent(this, NotificationAccessGuideActivity.class)));
        optionalCard.addView(notificationAccessButton, UiKit.spaced(this, 10));
        root.addView(optionalCard, UiKit.spaced(this, 12));

        LinearLayout mascotCard = UiKit.card(this);
        mascotCard.addView(UiKit.sectionTitle(this, "角色与语音"), UiKit.matchWrap());
        mascotCard.addView(MascotUi.compactStatus(this), UiKit.matchWrap());
        Button mascotButton = UiKit.entryButton(this, "角色与语音", "选择角色槽位、导入图标和语音");
        mascotButton.setOnClickListener(v -> startActivity(new Intent(this, MascotSettingsActivity.class)));
        mascotCard.addView(mascotButton, UiKit.spaced(this, 10));
        root.addView(mascotCard, UiKit.spaced(this, 12));

        LinearLayout diagnosticCard = UiKit.card(this);
        diagnosticCard.addView(UiKit.sectionTitle(this, "诊断"), UiKit.matchWrap());
        TextView diagnosticHint = UiKit.hint(this, "复现问题后，在诊断中心复制日志和最近关键事件。");
        diagnosticHint.setPadding(0, 0, 0, UiKit.dp(this, 8));
        diagnosticCard.addView(diagnosticHint, UiKit.matchWrap());
        Button diagnosticButton = UiKit.entryButton(this, "诊断中心", "复制日志、查看最近事件和权限状态");
        diagnosticButton.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticActivity.class)));
        diagnosticCard.addView(diagnosticButton, UiKit.matchWrap());
        root.addView(diagnosticCard, UiKit.spaced(this, 12));

        updateStatus();
        return UiKit.scrollScreen(this, root);
    }

    private void addStatusRow(LinearLayout parent, String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 5));

        TextView labelView = UiKit.text(this, label, 14, UiKit.COLOR_MUTED, false);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parent.addView(row, UiKit.matchWrap());
    }

    private void updateStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        if (accessibilityStatusView != null) {
            accessibilityStatusView.setText(accessibilityEnabled ? "已开启" : "未开启");
            accessibilityStatusView.setTextColor(accessibilityEnabled ? UiKit.COLOR_SUCCESS : UiKit.COLOR_DANGER);
        }

        boolean mediaEnabled = isNotificationListenerEnabled();
        if (mediaStatusView != null) {
            mediaStatusView.setText(mediaEnabled ? "已开启" : "未开启");
            mediaStatusView.setTextColor(mediaEnabled ? UiKit.COLOR_SUCCESS : UiKit.COLOR_DANGER);
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

}
