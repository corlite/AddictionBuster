package com.addictionbuster;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

public class BusterAccessibilityService extends AccessibilityService {
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_TARGET_LABEL = "target_label";
    private static final int WINDOW_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
    private static final int CHALLENGE_SECONDS = 15;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View challengeOverlay;
    private TextView overlayTimerView;
    private TextView overlayBreathView;
    private Button overlayFiveMinuteButton;
    private Button overlayTenMinuteButton;
    private Runnable overlayTick;
    private String overlayTargetPackage;
    private int overlayRemaining;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        RuleStore.clearChallengePackage(this);
        DiagnosticLogger.log(this, "service", "connected blockedPackages=" + RuleStore.getBlockedPackages(this));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || (event.getEventType() & WINDOW_EVENT_TYPES) == 0) {
            return;
        }
        CharSequence packageValue = event.getPackageName();
        if (packageValue == null) {
            DiagnosticLogger.log(this, "event", "window state changed but package is null");
            return;
        }

        String packageName = packageValue.toString();
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        Set<String> blockedPackages = RuleStore.getBlockedPackages(this);
        String passthroughPackage = RuleStore.getPassthroughPackage(this);
        String activeChallenge = RuleStore.getChallengePackage(this);
        boolean blocked = blockedPackages.contains(packageName);
        boolean passthrough = packageName.equals(passthroughPackage);
        long passthroughRemainingSeconds = passthrough
                ? RuleStore.getPassthroughRemainingSeconds(this, packageName)
                : 0L;

        DiagnosticLogger.log(
                this,
                "event",
                "window type=" + event.getEventType()
                        + " package=" + packageName
                        + " class=" + className
                        + " blocked=" + blocked
                        + " passthrough=" + passthrough
                        + " passthroughRemainingSeconds=" + passthroughRemainingSeconds
                        + " activeChallenge=" + activeChallenge
        );

        if (getPackageName().equals(packageName)) {
            DiagnosticLogger.log(this, "service", "ignore own package=" + packageName);
            return;
        }

        RuleStore.clearExpiredPassthrough(this);

        if (!blocked) {
            DiagnosticLogger.log(this, "service", "allow because package is not blocked: " + packageName);
            return;
        }
        if (passthrough) {
            DiagnosticLogger.log(this, "service", "allow because passthrough is active: " + packageName + " remainingSeconds=" + passthroughRemainingSeconds);
            return;
        }

        if (packageName.equals(activeChallenge)) {
            if (challengeOverlay != null) {
                DiagnosticLogger.log(this, "service", "challenge already active for package=" + packageName);
            } else {
                DiagnosticLogger.log(this, "service", "stale active challenge without overlay; retry package=" + packageName);
                RuleStore.clearChallengePackage(this);
                showChallengeOverlay(packageName, loadLabel(packageName));
            }
            return;
        }

        showChallengeOverlay(packageName, loadLabel(packageName));
    }

    @Override
    public void onInterrupt() {
        DiagnosticLogger.log(this, "service", "accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        removeChallengeOverlay("service destroyed", true);
        super.onDestroy();
    }

    private void showChallengeOverlay(String packageName, String label) {
        if (challengeOverlay != null) {
            DiagnosticLogger.log(this, "challenge", "overlay already visible for package=" + overlayTargetPackage);
            return;
        }

        RuleStore.setChallengePackage(this, packageName);
        overlayTargetPackage = packageName;
        overlayRemaining = CHALLENGE_SECONDS;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(46), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        TextView eyebrow = text("先停一下", 18, Color.rgb(37, 99, 235), true);
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, matchWrap());

        TextView title = text("你正在打开\n" + label, 28, Color.rgb(15, 23, 42), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(12));
        root.addView(title, matchWrap());

        overlayTimerView = text("", 58, Color.rgb(15, 23, 42), true);
        overlayTimerView.setGravity(Gravity.CENTER);
        overlayTimerView.setPadding(0, dp(22), 0, dp(12));
        root.addView(overlayTimerView, matchWrap());

        overlayBreathView = text("", 18, Color.rgb(71, 85, 105), false);
        overlayBreathView.setGravity(Gravity.CENTER);
        overlayBreathView.setPadding(0, 0, 0, dp(28));
        root.addView(overlayBreathView, matchWrap());

        overlayFiveMinuteButton = new Button(this);
        overlayFiveMinuteButton.setText("请先完成呼吸");
        overlayFiveMinuteButton.setAllCaps(false);
        overlayFiveMinuteButton.setEnabled(false);
        overlayFiveMinuteButton.setOnClickListener(v -> continueFromOverlay(5));
        root.addView(overlayFiveMinuteButton, matchWrap());

        overlayTenMinuteButton = new Button(this);
        overlayTenMinuteButton.setText("请先完成呼吸");
        overlayTenMinuteButton.setAllCaps(false);
        overlayTenMinuteButton.setEnabled(false);
        overlayTenMinuteButton.setOnClickListener(v -> continueFromOverlay(10));
        root.addView(overlayTenMinuteButton, matchWrap());

        Button quitButton = new Button(this);
        quitButton.setText("算了，回到桌面");
        quitButton.setAllCaps(false);
        quitButton.setOnClickListener(v -> goHomeFromOverlay());
        root.addView(quitButton, matchWrap());

        TextView hint = text("完成后可选择本次使用 5 分钟或 10 分钟。时间到期后，再打开会重新拦截。", 14, Color.rgb(100, 116, 139), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(22), 0, 0);
        root.addView(hint, matchWrap());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            windowManager.addView(root, params);
            challengeOverlay = root;
            DiagnosticLogger.log(this, "challenge", "show overlay package=" + packageName + " label=" + label);
            startOverlayCountdown();
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "challenge", "failed to show overlay package=" + packageName + " error=" + exception);
            RuleStore.clearChallengePackage(this);
            launchChallengeActivityFallback(packageName, label);
        }
    }

    private void startOverlayCountdown() {
        overlayTick = new Runnable() {
            @Override
            public void run() {
                updateOverlayCountdown();
                if (overlayRemaining <= 0) {
                    overlayFiveMinuteButton.setEnabled(true);
                    overlayFiveMinuteButton.setText("允许 5 分钟");
                    overlayTenMinuteButton.setEnabled(true);
                    overlayTenMinuteButton.setText("允许 10 分钟");
                    overlayBreathView.setText("现在再决定一次：你真的要打开它吗？");
                    DiagnosticLogger.log(BusterAccessibilityService.this, "challenge", "overlay countdown complete package=" + overlayTargetPackage);
                    return;
                }
                overlayRemaining--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(overlayTick);
    }

    private void updateOverlayCountdown() {
        overlayTimerView.setText(String.valueOf(overlayRemaining));
        int phase = overlayRemaining % 6;
        if (phase >= 4) {
            overlayBreathView.setText("慢慢吸气");
        } else if (phase >= 2) {
            overlayBreathView.setText("停一停，观察这个冲动");
        } else {
            overlayBreathView.setText("慢慢呼气");
        }
    }

    private void continueFromOverlay(int minutes) {
        String packageName = overlayTargetPackage;
        DiagnosticLogger.log(this, "challenge", "overlay continue package=" + packageName + " minutes=" + minutes);
        RuleStore.grantPassthrough(this, packageName, minutes);
        removeChallengeOverlay("continue", false);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try {
                startActivity(launchIntent);
            } catch (RuntimeException exception) {
                DiagnosticLogger.log(this, "challenge", "failed to launch target after overlay package=" + packageName + " error=" + exception);
            }
        } else {
            DiagnosticLogger.log(this, "challenge", "target launch intent is null after overlay package=" + packageName);
        }
    }

    private void goHomeFromOverlay() {
        DiagnosticLogger.log(this, "challenge", "overlay quit package=" + overlayTargetPackage);
        removeChallengeOverlay("quit", true);
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void removeChallengeOverlay(String reason, boolean clearChallenge) {
        if (overlayTick != null) {
            handler.removeCallbacks(overlayTick);
            overlayTick = null;
        }
        if (challengeOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(challengeOverlay);
                DiagnosticLogger.log(this, "challenge", "remove overlay reason=" + reason + " package=" + overlayTargetPackage);
            } catch (RuntimeException exception) {
                DiagnosticLogger.log(this, "challenge", "failed to remove overlay reason=" + reason + " error=" + exception);
            }
        }
        challengeOverlay = null;
        overlayTimerView = null;
        overlayBreathView = null;
        overlayFiveMinuteButton = null;
        overlayTenMinuteButton = null;
        overlayTargetPackage = null;
        overlayRemaining = 0;
        if (clearChallenge) {
            RuleStore.clearChallengePackage(this);
        }
    }

    private void launchChallengeActivityFallback(String packageName, String label) {
        RuleStore.setChallengePackage(this, packageName);
        Intent intent = new Intent(this, ChallengeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_TARGET_PACKAGE, packageName);
        intent.putExtra(EXTRA_TARGET_LABEL, label);
        try {
            DiagnosticLogger.log(this, "challenge", "fallback launch activity package=" + packageName + " label=" + label);
            startActivity(intent);
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "challenge", "failed fallback launch activity package=" + packageName + " error=" + exception);
            RuleStore.clearChallengePackage(this);
        }
    }

    private String loadLabel(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            return packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
            ).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
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
