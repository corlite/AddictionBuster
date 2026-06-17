package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ActiveAppsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "main", "active apps screen opened");
        setContentView(buildContent());
        MascotSoundPlayer.play(this, MascotVoiceSlot.ACTIVE_APPS);
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("生效应用", 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text("这里显示已经启用拦截的所有应用。点进去可以查看和修改规则。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        populateList(list);
        addAddAppLink(list);
        return root;
    }

    private void populateList(LinearLayout list) {
        Set<String> blockedPackages = RuleStore.getBlockedPackages(this);
        if (blockedPackages.isEmpty()) {
            TextView empty = text("还没有生效应用。\n去“增加应用”里选择一个要拦截的 App。", 16, Color.rgb(100, 116, 139), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(44), 0, 0);
            list.addView(empty, matchWrap());
            return;
        }

        List<String> packages = new ArrayList<>(blockedPackages);
        packages.sort(String::compareToIgnoreCase);
        for (String packageName : packages) {
            String label = AppCatalog.loadLabel(this, packageName);
            LinearLayout row = appRow(packageName, label);
            row.setOnClickListener(v -> startActivity(AppRuleActivity.intentFor(this, packageName, label)));
            list.addView(row, matchWrap());
        }
    }

    private void addAddAppLink(LinearLayout list) {
        TextView addLink = text("增加应用", 20, Color.rgb(37, 99, 235), true);
        addLink.setPadding(0, dp(16), 0, dp(8));
        addLink.setOnClickListener(v -> startActivity(new Intent(this, AddAppActivity.class)));
        list.addView(addLink, matchWrap());
    }

    private LinearLayout appRow(String packageName, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(AppCatalog.loadIcon(this, packageName));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        TextView labelView = text(label, 16, Color.rgb(15, 23, 42), true);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return row;
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
