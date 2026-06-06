package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ChallengeActivity extends Activity {
    private static final int CHALLENGE_SECONDS = 15;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private String targetLabel;
    private int remaining = CHALLENGE_SECONDS;
    private TextView timerView;
    private TextView breathView;
    private Button continueButton;
    private boolean completed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            if (remaining <= 0) {
                completed = true;
                continueButton.setEnabled(true);
                continueButton.setText("继续打开");
                breathView.setText("现在再决定一次：你真的要打开它吗？");
                return;
            }
            remaining--;
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetPackage = getIntent().getStringExtra(BusterAccessibilityService.EXTRA_TARGET_PACKAGE);
        targetLabel = getIntent().getStringExtra(BusterAccessibilityService.EXTRA_TARGET_LABEL);
        if (targetPackage == null) {
            finish();
            return;
        }
        if (targetLabel == null || targetLabel.trim().isEmpty()) {
            targetLabel = targetPackage;
        }

        setContentView(buildContent());
        handler.post(tick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!completed) {
            RuleStore.clearChallengePackage(this);
        }
        super.onDestroy();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(46), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView eyebrow = text("先停一下", 18, Color.rgb(37, 99, 235), true);
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, matchWrap());

        TextView title = text("你正在打开\n" + targetLabel, 28, Color.rgb(15, 23, 42), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(12));
        root.addView(title, matchWrap());

        timerView = text("", 58, Color.rgb(15, 23, 42), true);
        timerView.setGravity(Gravity.CENTER);
        timerView.setPadding(0, dp(22), 0, dp(12));
        root.addView(timerView, matchWrap());

        breathView = text("", 18, Color.rgb(71, 85, 105), false);
        breathView.setGravity(Gravity.CENTER);
        breathView.setPadding(0, 0, 0, dp(28));
        root.addView(breathView, matchWrap());

        continueButton = new Button(this);
        continueButton.setText("请先完成呼吸");
        continueButton.setAllCaps(false);
        continueButton.setEnabled(false);
        continueButton.setOnClickListener(v -> continueToTarget());
        root.addView(continueButton, matchWrap());

        Button quitButton = new Button(this);
        quitButton.setText("算了，回到桌面");
        quitButton.setAllCaps(false);
        quitButton.setOnClickListener(v -> goHome());
        root.addView(quitButton, matchWrap());

        TextView hint = text("完成后只放行这一次。离开目标应用后，下次打开会再次拦截。", 14, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(22), 0, 0);
        root.addView(hint, matchWrap());

        return root;
    }

    private void updateCountdown() {
        timerView.setText(String.valueOf(remaining));
        int phase = remaining % 6;
        if (phase >= 4) {
            breathView.setText("慢慢吸气");
        } else if (phase >= 2) {
            breathView.setText("停一停，观察这个冲动");
        } else {
            breathView.setText("慢慢呼气");
        }
    }

    private void continueToTarget() {
        completed = true;
        RuleStore.grantPassthrough(this, targetPackage);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launchIntent);
        }
        finish();
    }

    private void goHome() {
        RuleStore.clearChallengePackage(this);
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
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
