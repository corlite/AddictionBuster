package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ChallengeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private String targetLabel;
    private AppRule rule = AppRule.defaults();
    private int remaining;
    private TextView timerView;
    private TextView breathView;
    private Button tapButton;
    private EditText confirmInput;
    private Button confirmButton;
    private Button fiveMinuteButton;
    private Button tenMinuteButton;
    private boolean completed;
    private boolean continuedToTarget;
    private int tapCount;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            if (remaining <= 0) {
                beginInteractions();
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
        if (V2RuntimeMode.isEnabled(this)) {
            DiagnosticLogger.log(this, "challenge", "finish legacy ChallengeActivity because v2 enforcement is enabled");
            finish();
            return;
        }
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
        rule = RuleStore.getAppRule(this, targetPackage);
        remaining = rule.waitSeconds;

        DiagnosticLogger.log(this, "challenge", "created targetPackage=" + targetPackage + " targetLabel=" + targetLabel
                + " waitSeconds=" + rule.waitSeconds
                + " requiredTaps=" + rule.requiredTaps
                + " confirmTextLength=" + rule.confirmText.length());
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

        TextView eyebrow = text(getString(R.string.challenge_eyebrow), 18, Color.rgb(37, 99, 235), true);
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, matchWrap());

        TextView title = text(getString(R.string.challenge_title_format, targetLabel), 28, Color.rgb(15, 23, 42), true);
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

        tapButton = new Button(this);
        tapButton.setText("");
        tapButton.setAllCaps(false);
        tapButton.setVisibility(View.GONE);
        tapButton.setOnClickListener(v -> handleTapChallenge());
        root.addView(tapButton, matchWrap());

        confirmInput = new EditText(this);
        confirmInput.setSingleLine(true);
        confirmInput.setHint(R.string.challenge_confirm_hint);
        confirmInput.setVisibility(View.GONE);
        root.addView(confirmInput, matchWrap());

        confirmButton = new Button(this);
        confirmButton.setText(R.string.challenge_confirm_button);
        confirmButton.setAllCaps(false);
        confirmButton.setVisibility(View.GONE);
        confirmButton.setOnClickListener(v -> validateConfirmText());
        root.addView(confirmButton, matchWrap());

        fiveMinuteButton = new Button(this);
        fiveMinuteButton.setText(R.string.challenge_complete_first);
        fiveMinuteButton.setAllCaps(false);
        fiveMinuteButton.setEnabled(false);
        root.addView(fiveMinuteButton, matchWrap());

        tenMinuteButton = new Button(this);
        tenMinuteButton.setText(R.string.challenge_complete_first);
        tenMinuteButton.setAllCaps(false);
        tenMinuteButton.setEnabled(false);
        root.addView(tenMinuteButton, matchWrap());

        Button quitButton = new Button(this);
        quitButton.setText(R.string.challenge_quit);
        quitButton.setAllCaps(false);
        quitButton.setOnClickListener(v -> goHome());
        root.addView(quitButton, matchWrap());

        TextView hint = text(getString(R.string.challenge_hint), 14, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(22), 0, 0);
        root.addView(hint, matchWrap());

        return root;
    }

    private void updateCountdown() {
        timerView.setText(String.valueOf(remaining));
        if (rule.waitSeconds <= 0) {
            breathView.setText(R.string.challenge_no_wait);
            return;
        }
        int phase = remaining % 6;
        if (phase >= 4) {
            breathView.setText(R.string.challenge_breathe_in);
        } else if (phase >= 2) {
            breathView.setText(R.string.challenge_pause_impulse);
        } else {
            breathView.setText(R.string.challenge_breathe_out);
        }
    }

    private void beginInteractions() {
        if (completed) {
            return;
        }
        timerView.setText("0");
        if (rule.requiredTaps > 0) {
            tapButton.setVisibility(View.VISIBLE);
            updateTapButton();
            breathView.setText(R.string.challenge_tap_prompt);
            return;
        }
        beginConfirmOrComplete();
    }

    private void handleTapChallenge() {
        tapCount++;
        DiagnosticLogger.log(this, "challenge", "activity action tap targetPackage=" + targetPackage
                + " taps=" + tapCount + "/" + rule.requiredTaps);
        if (tapCount >= rule.requiredTaps) {
            tapButton.setVisibility(View.GONE);
            beginConfirmOrComplete();
            return;
        }
        updateTapButton();
    }

    private void updateTapButton() {
        int remainingTaps = Math.max(0, rule.requiredTaps - tapCount);
        tapButton.setText(getString(R.string.challenge_taps_remaining_format, remainingTaps));
    }

    private void beginConfirmOrComplete() {
        if (rule.confirmText.isEmpty()) {
            completeChallenge();
            return;
        }
        breathView.setText(R.string.challenge_text_prompt);
        confirmInput.setHint(getString(R.string.challenge_confirm_prompt_format, rule.confirmText));
        confirmInput.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
    }

    private void validateConfirmText() {
        String actual = confirmInput.getText().toString().trim();
        if (rule.confirmText.equals(actual)) {
            DiagnosticLogger.log(this, "challenge", "activity confirm matched targetPackage=" + targetPackage);
            confirmInput.setVisibility(View.GONE);
            confirmButton.setVisibility(View.GONE);
            completeChallenge();
        } else {
            confirmInput.setError(getString(R.string.challenge_confirm_error));
            DiagnosticLogger.log(this, "challenge", "activity confirm mismatch targetPackage=" + targetPackage);
        }
    }

    private void completeChallenge() {
        completed = true;
        int sessionLimit = Math.max(1, rule.sessionLimitMinutes);
        long dailyRemainingSeconds = RuleStore.getDailyRemainingSeconds(this, targetPackage, rule);
        if (dailyRemainingSeconds <= 0L) {
            fiveMinuteButton.setEnabled(false);
            fiveMinuteButton.setText(R.string.challenge_quota_exhausted);
            tenMinuteButton.setVisibility(View.GONE);
            breathView.setText(R.string.challenge_quota_exhausted_body);
            DiagnosticLogger.log(this, "challenge", "activity quota exhausted targetPackage=" + targetPackage);
            return;
        }
        if (dailyRemainingSeconds != Long.MAX_VALUE) {
            int remainingMinutes = Math.max(1, (int) Math.ceil(dailyRemainingSeconds / 60.0));
            sessionLimit = Math.min(sessionLimit, remainingMinutes);
        }
        int firstMinutes = Math.min(5, sessionLimit);
        int secondMinutes = sessionLimit;
        fiveMinuteButton.setEnabled(true);
        fiveMinuteButton.setText(getString(R.string.challenge_allow_minutes_format, firstMinutes));
        fiveMinuteButton.setOnClickListener(v -> continueToTarget(firstMinutes));
        if (secondMinutes > firstMinutes) {
            tenMinuteButton.setVisibility(View.VISIBLE);
            tenMinuteButton.setEnabled(true);
            tenMinuteButton.setText(getString(R.string.challenge_allow_minutes_format, secondMinutes));
            tenMinuteButton.setOnClickListener(v -> continueToTarget(secondMinutes));
        } else {
            tenMinuteButton.setVisibility(View.GONE);
        }
        breathView.setText(R.string.challenge_decide_again);
    }

    private void continueToTarget(int minutes) {
        completed = true;
        continuedToTarget = true;
        minutes = Math.min(minutes, Math.max(1, rule.sessionLimitMinutes));
        long dailyRemainingSeconds = RuleStore.getDailyRemainingSeconds(this, targetPackage, rule);
        if (dailyRemainingSeconds <= 0L) {
            DiagnosticLogger.log(this, "challenge", "deny continue because quota exhausted targetPackage=" + targetPackage);
            return;
        }
        if (dailyRemainingSeconds != Long.MAX_VALUE) {
            minutes = Math.min(minutes, Math.max(1, (int) Math.ceil(dailyRemainingSeconds / 60.0)));
        }
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
