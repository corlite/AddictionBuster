package com.addictionbuster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class AppRuleActivity extends Activity {
    static final String EXTRA_PACKAGE_NAME = "package_name";
    static final String EXTRA_LABEL = "label";

    private String packageName;
    private String label;
    private EditText dailyQuotaInput;
    private EditText sessionLimitInput;
    private EditText waitSecondsInput;
    private EditText requiredTapsInput;
    private EditText hiddenCountInput;
    private EditText hiddenSecondsInput;
    private EditText confirmTextInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (packageName == null || packageName.trim().isEmpty()) {
            DiagnosticLogger.log(this, "rule", "finish rule screen because package is missing");
            finish();
            return;
        }
        if (label == null || label.trim().isEmpty()) {
            label = AppCatalog.loadLabel(this, packageName);
        }
        DiagnosticLogger.log(this, "rule", "rule screen opened package=" + packageName + " label=" + label);
        setContentView(buildContent());
        MascotSoundPlayer.play(this, MascotVoiceSlot.APP_RULE);
    }

    static Intent intentFor(Activity activity, String packageName, String label) {
        Intent intent = new Intent(activity, AppRuleActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_LABEL, label);
        return intent;
    }

    private ScrollView buildContent() {
        LinearLayout root = UiKit.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView eyebrow = UiKit.title(this, getString(R.string.app_rule_title));
        header.addView(eyebrow, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView saveLink = UiKit.text(this, getString(R.string.action_save), 18, UiKit.COLOR_PRIMARY, true);
        saveLink.setGravity(Gravity.CENTER);
        saveLink.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        saveLink.setOnClickListener(v -> saveRule());
        header.addView(saveLink, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(header, UiKit.matchWrap());

        TextView title = UiKit.text(this, label, 20, UiKit.COLOR_TEXT, true);
        title.setPadding(0, UiKit.dp(this, 8), 0, 0);
        root.addView(title, UiKit.matchWrap());

        TextView packageView = UiKit.hint(this, packageName);
        packageView.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 16));
        root.addView(packageView, UiKit.matchWrap());

        LinearLayout permissionGuide = permissionGuide();
        if (permissionGuide != null) {
            root.addView(permissionGuide, UiKit.matchWrap());
        }

        AppRule rule = RuleStore.getAppRule(this, packageName);
        dailyQuotaInput = numberInput(rule.dailyQuotaMinutes);
        sessionLimitInput = numberInput(rule.sessionLimitMinutes);
        waitSecondsInput = numberInput(rule.waitSeconds);
        requiredTapsInput = numberInput(rule.requiredTaps);
        hiddenCountInput = numberInput(rule.hiddenCount);
        hiddenSecondsInput = numberInput(rule.hiddenSeconds);
        confirmTextInput = textInput(rule.confirmText);

        LinearLayout quotaCard = UiKit.card(this);
        quotaCard.addView(UiKit.sectionTitle(this, getString(R.string.section_usage_quota)), UiKit.matchWrap());
        quotaCard.addView(field(getString(R.string.field_daily_quota), getString(R.string.hint_daily_quota), dailyQuotaInput), UiKit.matchWrap());
        quotaCard.addView(field(getString(R.string.field_session_limit), getString(R.string.hint_session_limit), sessionLimitInput), UiKit.matchWrap());
        root.addView(quotaCard, UiKit.matchWrap());

        LinearLayout challengeCard = UiKit.card(this);
        challengeCard.addView(UiKit.sectionTitle(this, getString(R.string.section_challenge_settings)), UiKit.matchWrap());
        challengeCard.addView(field(getString(R.string.field_wait_countdown), getString(R.string.hint_wait_countdown), waitSecondsInput), UiKit.matchWrap());
        challengeCard.addView(field(getString(R.string.field_required_taps), getString(R.string.hint_required_taps), requiredTapsInput), UiKit.matchWrap());
        challengeCard.addView(field(getString(R.string.field_hidden_count), getString(R.string.hint_hidden_count), hiddenCountInput), UiKit.matchWrap());
        challengeCard.addView(field(getString(R.string.field_hidden_seconds), getString(R.string.hint_hidden_seconds), hiddenSecondsInput), UiKit.matchWrap());
        root.addView(challengeCard, UiKit.spaced(this, 12));

        LinearLayout confirmCard = UiKit.card(this);
        confirmCard.addView(UiKit.sectionTitle(this, getString(R.string.section_text_confirmation)), UiKit.matchWrap());
        confirmCard.addView(field(getString(R.string.field_confirm_text), getString(R.string.hint_confirm_text), confirmTextInput), UiKit.matchWrap());
        root.addView(confirmCard, UiKit.spaced(this, 12));

        Button saveButton = UiKit.primaryButton(this, getString(R.string.action_save_rule));
        saveButton.setOnClickListener(v -> saveRule());
        root.addView(saveButton, UiKit.spaced(this, 16));

        LinearLayout dangerCard = UiKit.card(this);
        dangerCard.addView(UiKit.sectionTitle(this, getString(R.string.section_danger_actions)), UiKit.matchWrap());
        TextView dangerHint = UiKit.hint(this, getString(R.string.hint_disable_app));
        dangerHint.setPadding(0, 0, 0, UiKit.dp(this, 8));
        dangerCard.addView(dangerHint, UiKit.matchWrap());
        Button disableButton = UiKit.dangerButton(this, getString(R.string.action_disable_app));
        disableButton.setOnClickListener(v -> disableRule());
        dangerCard.addView(disableButton, UiKit.matchWrap());
        root.addView(dangerCard, UiKit.spaced(this, 16));

        TextView hint = UiKit.hint(this, getString(R.string.hint_rule_saved));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, UiKit.dp(this, 16), 0, 0);
        root.addView(hint, UiKit.matchWrap());

        return UiKit.scrollScreen(this, root);
    }

    private LinearLayout permissionGuide() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean mediaEnabled = isNotificationListenerEnabled();
        if (accessibilityEnabled && mediaEnabled) {
            return null;
        }

        LinearLayout guide = UiKit.card(this);

        TextView title = UiKit.text(this, getString(R.string.permission_guide_title), 17, UiKit.COLOR_DANGER, true);
        guide.addView(title, UiKit.matchWrap());

        TextView body = UiKit.body(
                this,
                getString(R.string.permission_guide_body)
        );
        body.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 8));
        guide.addView(body, UiKit.matchWrap());

        if (!accessibilityEnabled) {
            Button accessibilityButton = UiKit.entryButton(this, getString(R.string.action_open_accessibility), getString(R.string.hint_open_accessibility));
            accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            guide.addView(accessibilityButton, UiKit.matchWrap());
        }

        if (!mediaEnabled) {
            Button mediaButton = UiKit.entryButton(this, getString(R.string.action_open_media_permission), getString(R.string.hint_open_media_permission));
            mediaButton.setOnClickListener(v -> startActivity(new Intent(this, NotificationAccessGuideActivity.class)));
            guide.addView(mediaButton, UiKit.spaced(this, 8));
        }

        TextView hint = UiKit.hint(this, getString(R.string.permission_guide_hint));
        hint.setPadding(0, UiKit.dp(this, 8), 0, 0);
        guide.addView(hint, UiKit.matchWrap());
        return guide;
    }

    private LinearLayout field(String title, String hint, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 11));

        TextView titleView = UiKit.text(this, title, 15, UiKit.COLOR_TEXT, true);
        box.addView(titleView, UiKit.matchWrap());

        TextView hintView = UiKit.hint(this, hint);
        hintView.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 5));
        box.addView(hintView, UiKit.matchWrap());

        box.addView(input, UiKit.matchWrap());
        return box;
    }

    private EditText numberInput(int value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextSize(16);
        input.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 8), 0);
        return input;
    }

    private EditText textInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setTextSize(16);
        input.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 8), 0);
        return input;
    }

    private void saveRule() {
        try {
            AppRule rule = new AppRule(
                    intValue(dailyQuotaInput, AppRule.DEFAULT_DAILY_QUOTA_MINUTES),
                    intValue(sessionLimitInput, AppRule.DEFAULT_SESSION_LIMIT_MINUTES),
                    intValue(waitSecondsInput, AppRule.DEFAULT_WAIT_SECONDS),
                    intValue(requiredTapsInput, AppRule.DEFAULT_REQUIRED_TAPS),
                    intValue(hiddenCountInput, AppRule.DEFAULT_HIDDEN_COUNT),
                    intValue(hiddenSecondsInput, AppRule.DEFAULT_HIDDEN_SECONDS),
                    confirmTextInput.getText().toString()
            );
            Set<String> blockedPackages = RuleStore.getBlockedPackages(this);
            blockedPackages.add(packageName);
            RuleStore.saveBlockedPackages(this, blockedPackages);
            RuleStore.saveAppRule(this, packageName, rule);
            V2RuleBridge.saveAppRule(this, packageName, rule);
            Toast.makeText(this, R.string.toast_rule_saved, Toast.LENGTH_SHORT).show();
            finish();
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "rule", "failed to save rule package=" + packageName
                    + " error=" + exception.getMessage());
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void disableRule() {
        Set<String> blockedPackages = RuleStore.getBlockedPackages(this);
        blockedPackages.remove(packageName);
        RuleStore.saveBlockedPackages(this, blockedPackages);
        RuleStore.clearAppRule(this, packageName);
        V2RuleBridge.clearAppRule(this, packageName);
        Toast.makeText(this, R.string.toast_interception_disabled, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int intValue(EditText input, int fallback) {
        try {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
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
