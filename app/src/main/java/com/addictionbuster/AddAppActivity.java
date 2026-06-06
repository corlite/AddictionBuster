package com.addictionbuster;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AddAppActivity extends Activity {
    private LinearLayout appListView;
    private List<AppInfo> apps;
    private EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apps = AppCatalog.loadLaunchableApps(this);
        DiagnosticLogger.log(this, "main", "add app screen opened launchableApps=" + apps.size());
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("增加应用", 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView subtitle = text("搜索应用名或包名，打开右侧开关后就会加入拦截。", 15, Color.rgb(71, 85, 105), false);
        subtitle.setPadding(0, dp(8), 0, dp(12));
        root.addView(subtitle, matchWrap());

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索应用，例如 Bilibili / 哔哩哔哩");
        searchInput.setTextSize(16);
        searchInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAppList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(searchInput, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        appListView = new LinearLayout(this);
        appListView.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(appListView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        renderAppList();
        return root;
    }

    private void renderAppList() {
        if (appListView == null || apps == null) {
            return;
        }
        appListView.removeAllViews();
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        Set<String> selected = RuleStore.getBlockedPackages(this);
        int shown = 0;

        for (AppInfo app : apps) {
            if (!matches(app, query)) {
                continue;
            }
            shown++;
            appListView.addView(appRow(app, selected.contains(app.packageName)), matchWrap());
        }

        if (shown == 0) {
            TextView empty = text("没有找到匹配的应用。可以试试应用中文名、英文名或包名。", 15, Color.rgb(100, 116, 139), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, 0);
            appListView.addView(empty, matchWrap());
        }
    }

    private boolean matches(AppInfo app, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return app.label.toLowerCase(Locale.ROOT).contains(query)
                || app.packageName.toLowerCase(Locale.ROOT).contains(query);
    }

    private LinearLayout appRow(AppInfo app, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView label = text(app.label, 16, Color.rgb(15, 23, 42), true);
        textBox.addView(label, matchWrap());

        TextView packageView = text(app.packageName, 12, Color.rgb(100, 116, 139), false);
        textBox.addView(packageView, matchWrap());

        row.addView(textBox, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        PillToggleView toggle = new PillToggleView(this);
        toggle.setChecked(checked);
        row.addView(toggle);

        row.setOnClickListener(v -> toggleBlocked(app, toggle));
        toggle.setOnClickListener(v -> toggleBlocked(app, toggle));
        return row;
    }

    private void toggleBlocked(AppInfo app, PillToggleView toggle) {
        Set<String> current = RuleStore.getBlockedPackages(this);
        boolean nextChecked = !current.contains(app.packageName);
        if (nextChecked) {
            current.add(app.packageName);
        } else {
            current.remove(app.packageName);
        }
        RuleStore.saveBlockedPackages(this, current);
        toggle.setChecked(nextChecked);
        DiagnosticLogger.log(this, "rule", (nextChecked ? "blocked " : "unblocked ") + app.packageName + " label=" + app.label);
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
