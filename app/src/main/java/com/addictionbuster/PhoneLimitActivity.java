package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class PhoneLimitActivity extends Activity {
    private EditText dailyLimitInput;
    private EditText sessionLimitInput;
    private TextView usageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "main", "phone limit screen opened");
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUsage();
    }

    private ScrollView buildContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scrollView.addView(root);

        TextView title = text(getString(R.string.phone_limit_title), 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text(getString(R.string.phone_limit_subtitle), 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, matchWrap());

        usageView = text("", 15, Color.rgb(30, 64, 175), true);
        usageView.setPadding(0, 0, 0, dp(14));
        root.addView(usageView, matchWrap());

        dailyLimitInput = numberInput(RuleStore.getPhoneDailyLimitMinutes(this));
        root.addView(field(getString(R.string.field_phone_daily_limit), getString(R.string.hint_phone_daily_limit), dailyLimitInput), matchWrap());

        sessionLimitInput = numberInput(RuleStore.getPhoneSessionLimitMinutes(this));
        root.addView(field(getString(R.string.field_phone_session_limit), getString(R.string.hint_phone_session_limit), sessionLimitInput), matchWrap());

        Button whitelistButton = new Button(this);
        whitelistButton.setText(R.string.action_choose_whitelist);
        whitelistButton.setAllCaps(false);
        whitelistButton.setOnClickListener(v -> startActivity(new Intent(this, PhoneWhitelistActivity.class)));
        root.addView(whitelistButton, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText(R.string.action_save_phone_limit);
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(v -> saveLimits());
        root.addView(saveButton, matchWrap());

        TextView hint = text(getString(R.string.phone_limit_hint), 13, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint, matchWrap());

        updateUsage();
        return scrollView;
    }

    private LinearLayout field(String title, String hint, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(7), 0, dp(11));

        TextView titleView = text(title, 16, Color.rgb(15, 23, 42), true);
        box.addView(titleView, matchWrap());

        TextView hintView = text(hint, 13, Color.rgb(100, 116, 139), false);
        hintView.setPadding(0, dp(3), 0, dp(5));
        box.addView(hintView, matchWrap());

        box.addView(input, matchWrap());
        return box;
    }

    private EditText numberInput(int value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextSize(16);
        return input;
    }

    private void saveLimits() {
        int dailyLimit = intValue(dailyLimitInput, 0);
        int sessionLimit = intValue(sessionLimitInput, 0);
        RuleStore.savePhoneLimits(this, dailyLimit, sessionLimit);
        V2RuleBridge.savePhoneLimits(this, dailyLimit, sessionLimit);
        updateUsage();
        Toast.makeText(this, R.string.toast_phone_limit_saved, Toast.LENGTH_SHORT).show();
    }

    private void updateUsage() {
        if (usageView == null) {
            return;
        }
        long usedMinutes = V2RuleBridge.getPhoneDailyUsedMinutes(this);
        int dailyLimit = RuleStore.getPhoneDailyLimitMinutes(this);
        int sessionLimit = RuleStore.getPhoneSessionLimitMinutes(this);
        String dailyText = dailyLimit <= 0
                ? getString(R.string.phone_daily_limit_disabled)
                : getString(R.string.phone_daily_limit_enabled, dailyLimit);
        String sessionText = sessionLimit <= 0
                ? getString(R.string.phone_session_limit_disabled)
                : getString(R.string.phone_session_limit_enabled, sessionLimit);
        usageView.setText(getString(R.string.phone_usage_status_format, usedMinutes, dailyText, sessionText));
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
