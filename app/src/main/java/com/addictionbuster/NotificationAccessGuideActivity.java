package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NotificationAccessGuideActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "permission", "notification access guide opened");
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text(getString(R.string.notification_guide_title), 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView summary = text(
                getString(R.string.notification_guide_summary),
                16,
                Color.rgb(51, 65, 85),
                false
        );
        summary.setPadding(0, dp(12), 0, dp(18));
        root.addView(summary, matchWrap());

        root.addView(section(
                getString(R.string.notification_guide_why_title),
                getString(R.string.notification_guide_why_body)
        ), matchWrap());

        root.addView(section(
                getString(R.string.notification_guide_not_needed_title),
                getString(R.string.notification_guide_not_needed_body)
        ), matchWrap());

        root.addView(section(
                getString(R.string.notification_guide_limits_title),
                getString(R.string.notification_guide_limits_body)
        ), matchWrap());

        Button openSettingsButton = new Button(this);
        openSettingsButton.setText(R.string.notification_guide_open_settings);
        openSettingsButton.setAllCaps(false);
        openSettingsButton.setOnClickListener(v -> {
            DiagnosticLogger.log(this, "permission", "open notification listener settings");
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        root.addView(openSettingsButton, matchWrap());

        Button backButton = new Button(this);
        backButton.setText(R.string.notification_guide_skip);
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, matchWrap());

        TextView hint = text(
                getString(R.string.notification_guide_hint),
                14,
                Color.rgb(100, 116, 139),
                false
        );
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint, matchWrap());

        return root;
    }

    private LinearLayout section(String heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(14));

        TextView headingView = text(heading, 17, Color.rgb(30, 64, 175), true);
        box.addView(headingView, matchWrap());

        TextView bodyView = text(body, 15, Color.rgb(51, 65, 85), false);
        bodyView.setPadding(0, dp(5), 0, 0);
        box.addView(bodyView, matchWrap());
        return box;
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
