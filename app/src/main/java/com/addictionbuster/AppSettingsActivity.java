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

        root.addView(UiKit.title(this, getString(R.string.settings_title)), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, getString(R.string.settings_subtitle)), UiKit.matchWrap());

        LinearLayout requiredCard = UiKit.card(this);
        requiredCard.addView(UiKit.sectionTitle(this, getString(R.string.section_required_permissions)), UiKit.matchWrap());
        accessibilityStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(requiredCard, getString(R.string.status_accessibility_service), accessibilityStatusView);
        Button accessibilityButton = UiKit.entryButton(
                this,
                getString(R.string.action_enable_accessibility),
                getString(R.string.action_enable_accessibility_subtitle)
        );
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        requiredCard.addView(accessibilityButton, UiKit.spaced(this, 10));
        root.addView(requiredCard, UiKit.matchWrap());

        LinearLayout optionalCard = UiKit.card(this);
        optionalCard.addView(UiKit.sectionTitle(this, getString(R.string.section_optional_capabilities)), UiKit.matchWrap());
        mediaStatusView = UiKit.text(this, "", 15, UiKit.COLOR_TEXT, true);
        addStatusRow(optionalCard, getString(R.string.status_background_media), mediaStatusView);
        Button notificationAccessButton = UiKit.entryButton(
                this,
                getString(R.string.action_enable_background_media),
                getString(R.string.action_enable_background_media_subtitle)
        );
        notificationAccessButton.setOnClickListener(v -> startActivity(new Intent(this, NotificationAccessGuideActivity.class)));
        optionalCard.addView(notificationAccessButton, UiKit.spaced(this, 10));
        root.addView(optionalCard, UiKit.spaced(this, 12));

        LinearLayout mascotCard = UiKit.card(this);
        mascotCard.addView(UiKit.sectionTitle(this, getString(R.string.section_mascot_voice)), UiKit.matchWrap());
        mascotCard.addView(MascotUi.compactStatus(this), UiKit.matchWrap());
        Button mascotButton = UiKit.entryButton(
                this,
                getString(R.string.action_mascot_voice),
                getString(R.string.action_mascot_voice_subtitle)
        );
        mascotButton.setOnClickListener(v -> startActivity(new Intent(this, MascotSettingsActivity.class)));
        mascotCard.addView(mascotButton, UiKit.spaced(this, 10));
        root.addView(mascotCard, UiKit.spaced(this, 12));

        LinearLayout diagnosticCard = UiKit.card(this);
        diagnosticCard.addView(UiKit.sectionTitle(this, getString(R.string.section_diagnostics)), UiKit.matchWrap());
        TextView diagnosticHint = UiKit.hint(this, getString(R.string.diagnostic_hint));
        diagnosticHint.setPadding(0, 0, 0, UiKit.dp(this, 8));
        diagnosticCard.addView(diagnosticHint, UiKit.matchWrap());
        Button diagnosticButton = UiKit.entryButton(
                this,
                getString(R.string.action_diagnostic_center),
                getString(R.string.action_diagnostic_center_subtitle)
        );
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
            accessibilityStatusView.setText(accessibilityEnabled ? R.string.status_enabled : R.string.status_disabled);
            accessibilityStatusView.setTextColor(accessibilityEnabled ? UiKit.COLOR_SUCCESS : UiKit.COLOR_DANGER);
        }

        boolean mediaEnabled = isNotificationListenerEnabled();
        if (mediaStatusView != null) {
            mediaStatusView.setText(mediaEnabled ? R.string.status_enabled : R.string.status_disabled);
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
