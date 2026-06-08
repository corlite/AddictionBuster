package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.addictionbuster.bootstrap.V2InitializationGate;
import com.addictionbuster.bootstrap.V2RequiredSetupActivity;

public class MainActivity extends Activity {
    private TextView selectedCountView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "main", "home screen created");
        if (V2RuntimeMode.isEnabled(this)) {
            V2EnforcementForegroundService.start(this);
        }
        if (V2InitializationGate.requiresSetup(this)) {
            startActivity(new Intent(this, V2RequiredSetupActivity.class));
        }
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSelectedCount();
        DiagnosticLogger.log(this, "main", "home onResume selected=" + RuleStore.getBlockedPackages(this).size());
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text(getString(R.string.app_name), 30, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text("选择应用、设置规则，然后把冲动挡在进入之前。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        selectedCountView = text("", 15, Color.rgb(30, 64, 175), true);
        selectedCountView.setPadding(0, 0, 0, dp(14));
        root.addView(selectedCountView, matchWrap());

        Button activeAppsButton = homeButton("生效应用", "查看已经启用拦截的应用");
        activeAppsButton.setOnClickListener(v -> startActivity(new Intent(this, ActiveAppsActivity.class)));
        root.addView(activeAppsButton, matchWrap());

        Button addAppsButton = homeButton("增加应用", "搜索并选择需要拦截的应用");
        addAppsButton.setOnClickListener(v -> startActivity(new Intent(this, AddAppActivity.class)));
        LinearLayout.LayoutParams addParams = matchWrap();
        addParams.setMargins(0, dp(14), 0, dp(14));
        root.addView(addAppsButton, addParams);

        Button phoneLimitButton = homeButton("手机时长限制", "设置每日总时长、单次打开手机时长和白名单");
        phoneLimitButton.setOnClickListener(v -> startActivity(new Intent(this, PhoneLimitActivity.class)));
        LinearLayout.LayoutParams phoneLimitParams = matchWrap();
        phoneLimitParams.setMargins(0, 0, 0, dp(14));
        root.addView(phoneLimitButton, phoneLimitParams);

        Button settingsButton = homeButton("设置", "开启权限、查看诊断日志");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, AppSettingsActivity.class)));
        root.addView(settingsButton, matchWrap());

        TextView hint = text("前台拦截依赖无障碍服务；后台媒体阻断需要通知使用权。", 13, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(22), 0, 0);
        root.addView(hint, matchWrap());

        updateSelectedCount();
        return root;
    }

    private Button homeButton(String title, String subtitle) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title + "\n" + subtitle);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setTextColor(Color.rgb(15, 23, 42));
        button.setTextSize(17);
        return button;
    }

    private void updateSelectedCount() {
        if (selectedCountView == null) {
            return;
        }
        int count = RuleStore.getBlockedPackages(this).size();
        selectedCountView.setText("当前生效应用：" + count + " 个");
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
