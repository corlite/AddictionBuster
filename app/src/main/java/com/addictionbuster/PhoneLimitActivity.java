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

        TextView title = text("手机时长限制", 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text("统计非白名单前台 App 的使用时间。填 0 表示关闭对应限制。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, matchWrap());

        usageView = text("", 15, Color.rgb(30, 64, 175), true);
        usageView.setPadding(0, 0, 0, dp(14));
        root.addView(usageView, matchWrap());

        dailyLimitInput = numberInput(RuleStore.getPhoneDailyLimitMinutes(this));
        root.addView(field("每日总时长（分钟）", "今天累计使用非白名单 App 的总额度。", dailyLimitInput), matchWrap());

        sessionLimitInput = numberInput(RuleStore.getPhoneSessionLimitMinutes(this));
        root.addView(field("单次打开手机时长（分钟）", "每次解锁后，连续使用非白名单 App 的最多时长。锁屏后重新计算。", sessionLimitInput), matchWrap());

        Button whitelistButton = new Button(this);
        whitelistButton.setText("选择白名单应用");
        whitelistButton.setAllCaps(false);
        whitelistButton.setOnClickListener(v -> startActivity(new Intent(this, PhoneWhitelistActivity.class)));
        root.addView(whitelistButton, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("保存手机时长限制");
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(v -> saveLimits());
        root.addView(saveButton, matchWrap());

        TextView hint = text("超过额度后，再打开非白名单 App 会被拦截。电话、系统界面和本应用不会计入。", 13, Color.rgb(100, 116, 139), false);
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
        updateUsage();
        Toast.makeText(this, "手机时长限制已保存", Toast.LENGTH_SHORT).show();
    }

    private void updateUsage() {
        if (usageView == null) {
            return;
        }
        long usedMinutes = RuleStore.getPhoneDailyUsedSeconds(this) / 60L;
        int dailyLimit = RuleStore.getPhoneDailyLimitMinutes(this);
        int sessionLimit = RuleStore.getPhoneSessionLimitMinutes(this);
        String dailyText = dailyLimit <= 0 ? "每日总时长：未开启" : "每日总时长：" + dailyLimit + " 分钟";
        String sessionText = sessionLimit <= 0 ? "单次打开手机：未开启" : "单次打开手机：" + sessionLimit + " 分钟";
        usageView.setText("今日已统计：" + usedMinutes + " 分钟\n" + dailyText + "\n" + sessionText);
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
