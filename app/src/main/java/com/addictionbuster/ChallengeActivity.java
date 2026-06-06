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
    private Button fiveMinuteButton;
    private Button tenMinuteButton;
    private boolean completed;
    private boolean continuedToTarget;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            if (remaining <= 0) {
                completed = true;
                fiveMinuteButton.setEnabled(true);
                fiveMinuteButton.setText("允许 5 分钟");
                tenMinuteButton.setEnabled(true);
                tenMinuteButton.setText("允许 10 分钟");
                breathView.setText("现在再决定一次：你真的要打开它吗？");
                DiagnosticLogger.log(ChallengeActivity.this, "challenge", "countdown complete targetPackage=" + targetPackage);
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
            DiagnosticLogger.log(this, "challenge", "finish because target package is null");
            finish();
            return;
        }
        if (targetLabel == null || targetLabel.trim().isEmpty()) {
            targetLabel = targetPackage;
        }

        DiagnosticLogger.log(this, "challenge", "created targetPackage=" + targetPackage + " targetLabel=" + targetLabel);
        setContentView(buildContent());
        handler.post(tick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!continuedToTarget) {
            DiagnosticLogger.log(this, "challenge", "destroy without continuing targetPackage=" + targetPackage);
            RuleStore.clearChallengePackage(this);
        } else {
            DiagnosticLogger.log(this, "challenge", "destroy after continuing to targetPackage=" + targetPackage);
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

        fiveMinuteButton = new Button(this);
        fiveMinuteButton.setText("请先完成呼吸");
        fiveMinuteButton.setAllCaps(false);
        fiveMinuteButton.setEnabled(false);
        fiveMinuteButton.setOnClickListener(v -> continueToTarget(5));
        root.addView(fiveMinuteButton, matchWrap());

        tenMinuteButton = new Button(this);
        tenMinuteButton.setText("请先完成呼吸");
        tenMinuteButton.setAllCaps(false);
        tenMinuteButton.setEnabled(false);
        tenMinuteButton.setOnClickListener(v -> continueToTarget(10));
        root.addView(tenMinuteButton, matchWrap());

        Button quitButton = new Button(this);
        quitButton.setText("算了，回到桌面");
        quitButton.setAllCaps(false);
        quitButton.setOnClickListener(v -> goHome());
        root.addView(quitButton, matchWrap());

        TextView hint = text("完成后可选择本次使用 5 分钟或 10 分钟。时间到期后，再打开会重新拦截。", 14, Color.rgb(100, 116, 139), false);
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

    private void continueToTarget(int minutes) {
        completed = true;
        continuedToTarget = true;
        RuleStore.grantPassthrough(this, targetPackage, minutes);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try {
                DiagnosticLogger.log(this, "challenge", "continue to target package=" + targetPackage + " minutes=" + minutes);
                startActivity(launchIntent);
            } catch (RuntimeException exception) {
                DiagnosticLogger.log(this, "challenge", "failed to continue target package=" + targetPackage + " error=" + exception);
            }
        } else {
            DiagnosticLogger.log(this, "challenge", "target launch intent is null package=" + targetPackage);
        }
        finish();
    }

    private void goHome() {
        DiagnosticLogger.log(this, "challenge", "user chose home targetPackage=" + targetPackage);
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
