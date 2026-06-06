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
    }

    static Intent intentFor(Activity activity, String packageName, String label) {
        Intent intent = new Intent(activity, AppRuleActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_LABEL, label);
        return intent;
    }

    private ScrollView buildContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scrollView.addView(root);

        TextView eyebrow = text("拦截规则", 16, Color.rgb(30, 64, 175), true);
        root.addView(eyebrow, matchWrap());

        TextView title = text(label, 27, Color.rgb(15, 23, 42), true);
        title.setPadding(0, dp(6), 0, 0);
        root.addView(title, matchWrap());

        TextView packageView = text(packageName, 13, Color.rgb(100, 116, 139), false);
        packageView.setPadding(0, dp(4), 0, dp(16));
        root.addView(packageView, matchWrap());

        AppRule rule = RuleStore.getAppRule(this, packageName);
        dailyQuotaInput = numberInput(rule.dailyQuotaMinutes);
        sessionLimitInput = numberInput(rule.sessionLimitMinutes);
        waitSecondsInput = numberInput(rule.waitSeconds);
        requiredTapsInput = numberInput(rule.requiredTaps);
        hiddenCountInput = numberInput(rule.hiddenCount);
        hiddenSecondsInput = numberInput(rule.hiddenSeconds);
        confirmTextInput = textInput(rule.confirmText);

        root.addView(field("每日额度（分钟）", "一天内最多允许使用多久，0 表示暂不限制。", dailyQuotaInput), matchWrap());
        root.addView(field("本次使用上限（分钟）", "挑战完成后单次最多放行多久。", sessionLimitInput), matchWrap());
        root.addView(field("等待倒计时（秒）", "打开被拦截应用前先等多久。", waitSecondsInput), matchWrap());
        root.addView(field("互动点击次数", "倒计时后还需要追着按钮点几次，0 表示不启用。", requiredTapsInput), matchWrap());
        root.addView(field("按钮隐藏次数", "互动过程中按钮随机隐藏几次，0 表示不隐藏。", hiddenCountInput), matchWrap());
        root.addView(field("每次隐藏时长（秒）", "按钮隐藏后多久再出现。", hiddenSecondsInput), matchWrap());
        root.addView(field("文字确认", "例如输入“我选择继续”。留空表示不需要文字确认。", confirmTextInput), matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("保存规则");
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(v -> saveRule());
        root.addView(saveButton, matchWrap());

        Button disableButton = new Button(this);
        disableButton.setText("停用这个应用拦截");
        disableButton.setAllCaps(false);
        disableButton.setOnClickListener(v -> disableRule());
        root.addView(disableButton, matchWrap());

        TextView hint = text("保存后，这个应用会继续出现在“生效应用”里。下一次打开它时，新规则会参与拦截。", 13, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(16), 0, 0);
        root.addView(hint, matchWrap());

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

    private EditText textInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setTextSize(16);
        return input;
    }

    private void saveRule() {
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
        Toast.makeText(this, "规则已保存", Toast.LENGTH_SHORT).show();
    }

    private void disableRule() {
        Set<String> blockedPackages = RuleStore.getBlockedPackages(this);
        blockedPackages.remove(packageName);
        RuleStore.saveBlockedPackages(this, blockedPackages);
        RuleStore.clearAppRule(this, packageName);
        Toast.makeText(this, "已停用拦截", Toast.LENGTH_SHORT).show();
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
