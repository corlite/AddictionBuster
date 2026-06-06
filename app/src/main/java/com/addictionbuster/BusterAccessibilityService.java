package com.addictionbuster;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;
import java.util.Set;

public class BusterAccessibilityService extends AccessibilityService {
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_TARGET_LABEL = "target_label";
    private static final int WINDOW_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private WindowManager windowManager;
    private View challengeOverlay;
    private TextView overlayTimerView;
    private TextView overlayBreathView;
    private Button overlayFiveMinuteButton;
    private Button overlayTenMinuteButton;
    private FrameLayout overlayActionArea;
    private Button overlayActionButton;
    private EditText overlayConfirmInput;
    private Button overlayConfirmButton;
    private Runnable overlayTick;
    private Runnable overlayUnhide;
    private Runnable passthroughExpiryCheck;
    private AppRule overlayRule;
    private String overlayTargetPackage;
    private int overlayRemaining;
    private int overlayTapCount;
    private int overlayHideCount;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        RuleStore.clearChallengePackage(this);
        BackgroundMediaBlocker.enforce(this, "accessibility service connected");
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
        BackgroundMediaBlocker.enforce(this, "window event package=" + packageName);

        if (!blocked) {
            DiagnosticLogger.log(this, "service", "allow because package is not blocked: " + packageName);
            return;
        }
        if (passthrough) {
            schedulePassthroughExpiryCheck(packageName);
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
        if (passthroughExpiryCheck != null) {
            handler.removeCallbacks(passthroughExpiryCheck);
            passthroughExpiryCheck = null;
        }
        removeChallengeOverlay("service destroyed", true);
        super.onDestroy();
    }

    private void showChallengeOverlay(String packageName, String label) {
        if (challengeOverlay != null) {
            DiagnosticLogger.log(this, "challenge", "overlay already visible for package=" + overlayTargetPackage);
            return;
        }

        BackgroundMediaBlocker.enforce(this, "show challenge overlay package=" + packageName);
        RuleStore.setChallengePackage(this, packageName);
        overlayTargetPackage = packageName;
        overlayRule = RuleStore.getAppRule(this, packageName);
        overlayRemaining = overlayRule.waitSeconds;
        overlayTapCount = 0;
        overlayHideCount = 0;

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

        overlayActionArea = new FrameLayout(this);
        overlayActionArea.setMinimumHeight(dp(170));
        overlayActionArea.setVisibility(View.GONE);
        overlayActionButton = new Button(this);
        overlayActionButton.setAllCaps(false);
        overlayActionButton.setOnClickListener(v -> handleOverlayActionClick());
        overlayActionArea.addView(overlayActionButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(overlayActionArea, matchWrap());

        overlayConfirmInput = new EditText(this);
        overlayConfirmInput.setSingleLine(true);
        overlayConfirmInput.setHint("输入确认文字");
        overlayConfirmInput.setVisibility(View.GONE);
        root.addView(overlayConfirmInput, matchWrap());

        overlayConfirmButton = new Button(this);
        overlayConfirmButton.setText("确认文字");
        overlayConfirmButton.setAllCaps(false);
        overlayConfirmButton.setVisibility(View.GONE);
        overlayConfirmButton.setOnClickListener(v -> validateOverlayConfirm());
        root.addView(overlayConfirmButton, matchWrap());

        overlayFiveMinuteButton = new Button(this);
        overlayFiveMinuteButton.setText("请先完成挑战");
        overlayFiveMinuteButton.setAllCaps(false);
        overlayFiveMinuteButton.setEnabled(false);
        root.addView(overlayFiveMinuteButton, matchWrap());

        overlayTenMinuteButton = new Button(this);
        overlayTenMinuteButton.setText("请先完成挑战");
        overlayTenMinuteButton.setAllCaps(false);
        overlayTenMinuteButton.setEnabled(false);
        root.addView(overlayTenMinuteButton, matchWrap());

        Button quitButton = new Button(this);
        quitButton.setText("算了，回到桌面");
        quitButton.setAllCaps(false);
        quitButton.setOnClickListener(v -> goHomeFromOverlay());
        root.addView(quitButton, matchWrap());

        TextView hint = text("完成规则后会按本次使用上限放行。时间到期后，再打开会重新拦截。", 14, Color.rgb(100, 116, 139), false);
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
            DiagnosticLogger.log(this, "challenge", "show overlay package=" + packageName + " label=" + label
                    + " waitSeconds=" + overlayRule.waitSeconds
                    + " requiredTaps=" + overlayRule.requiredTaps
                    + " hiddenCount=" + overlayRule.hiddenCount
                    + " confirmTextLength=" + overlayRule.confirmText.length());
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
                    DiagnosticLogger.log(BusterAccessibilityService.this, "challenge", "overlay countdown complete package=" + overlayTargetPackage);
                    beginOverlayInteractions();
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
        if (overlayRule != null && overlayRule.waitSeconds <= 0) {
            overlayBreathView.setText("这次不等待，直接进入规则确认。");
            return;
        }
        int phase = overlayRemaining % 6;
        if (phase >= 4) {
            overlayBreathView.setText("慢慢吸气");
        } else if (phase >= 2) {
            overlayBreathView.setText("停一停，观察这个冲动");
        } else {
            overlayBreathView.setText("慢慢呼气");
        }
    }

    private void beginOverlayInteractions() {
        if (overlayRule == null) {
            overlayRule = AppRule.defaults();
        }
        overlayTimerView.setText("0");
        if (overlayRule.requiredTaps > 0) {
            overlayBreathView.setText("再追着按钮点 " + overlayRule.requiredTaps + " 次，确认这不是自动冲动。");
            overlayActionArea.setVisibility(View.VISIBLE);
            updateOverlayActionButton();
            moveOverlayActionButton();
            return;
        }
        beginOverlayConfirmOrComplete();
    }

    private void handleOverlayActionClick() {
        if (overlayRule == null || overlayRule.requiredTaps <= 0 || overlayActionButton == null) {
            return;
        }
        overlayTapCount++;
        DiagnosticLogger.log(this, "challenge", "overlay action tap package=" + overlayTargetPackage
                + " taps=" + overlayTapCount + "/" + overlayRule.requiredTaps);
        if (overlayTapCount >= overlayRule.requiredTaps) {
            overlayActionArea.setVisibility(View.GONE);
            beginOverlayConfirmOrComplete();
            return;
        }

        updateOverlayActionButton();
        if (shouldHideOverlayActionButton()) {
            hideOverlayActionButton();
        } else {
            moveOverlayActionButton();
        }
    }

    private boolean shouldHideOverlayActionButton() {
        int hiddenRemaining = overlayRule.hiddenCount - overlayHideCount;
        if (hiddenRemaining <= 0) {
            return false;
        }
        int tapsRemaining = overlayRule.requiredTaps - overlayTapCount;
        return hiddenRemaining >= tapsRemaining || random.nextBoolean();
    }

    private void hideOverlayActionButton() {
        overlayHideCount++;
        overlayActionButton.setVisibility(View.INVISIBLE);
        overlayBreathView.setText("按钮先藏一下，别急着点。");
        if (overlayUnhide != null) {
            handler.removeCallbacks(overlayUnhide);
        }
        long delayMillis = Math.max(1, overlayRule.hiddenSeconds) * 1000L;
        overlayUnhide = () -> {
            if (challengeOverlay == null || overlayActionButton == null) {
                return;
            }
            overlayActionButton.setVisibility(View.VISIBLE);
            updateOverlayActionButton();
            moveOverlayActionButton();
        };
        handler.postDelayed(overlayUnhide, delayMillis);
        DiagnosticLogger.log(this, "challenge", "overlay action hidden package=" + overlayTargetPackage
                + " hidden=" + overlayHideCount + "/" + overlayRule.hiddenCount
                + " delayMillis=" + delayMillis);
    }

    private void updateOverlayActionButton() {
        int remainingTaps = Math.max(0, overlayRule.requiredTaps - overlayTapCount);
        overlayActionButton.setText("点我，还差 " + remainingTaps + " 次");
        overlayBreathView.setText("按钮会移动，点的时候慢一点。");
    }

    private void moveOverlayActionButton() {
        if (overlayActionArea == null || overlayActionButton == null) {
            return;
        }
        overlayActionArea.post(() -> {
            if (challengeOverlay == null || overlayActionArea == null || overlayActionButton == null) {
                return;
            }
            int buttonWidth = Math.max(dp(120), overlayActionButton.getMeasuredWidth());
            int buttonHeight = Math.max(dp(48), overlayActionButton.getMeasuredHeight());
            int maxLeft = Math.max(0, overlayActionArea.getWidth() - buttonWidth);
            int maxTop = Math.max(0, overlayActionArea.getHeight() - buttonHeight);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.leftMargin = maxLeft == 0 ? 0 : random.nextInt(maxLeft + 1);
            params.topMargin = maxTop == 0 ? 0 : random.nextInt(maxTop + 1);
            overlayActionButton.setLayoutParams(params);
        });
    }

    private void beginOverlayConfirmOrComplete() {
        if (overlayRule.confirmText.isEmpty()) {
            completeOverlayChallenge();
            return;
        }
        overlayBreathView.setText("输入确认文字，给自己一个清醒的停顿。");
        overlayConfirmInput.setHint("请输入：" + overlayRule.confirmText);
        overlayConfirmInput.setVisibility(View.VISIBLE);
        overlayConfirmButton.setVisibility(View.VISIBLE);
    }

    private void validateOverlayConfirm() {
        if (overlayRule == null || overlayConfirmInput == null) {
            return;
        }
        String actual = overlayConfirmInput.getText().toString().trim();
        if (overlayRule.confirmText.equals(actual)) {
            DiagnosticLogger.log(this, "challenge", "overlay confirm matched package=" + overlayTargetPackage);
            overlayConfirmInput.setVisibility(View.GONE);
            overlayConfirmButton.setVisibility(View.GONE);
            completeOverlayChallenge();
        } else {
            overlayConfirmInput.setError("请完整输入确认文字");
            DiagnosticLogger.log(this, "challenge", "overlay confirm mismatch package=" + overlayTargetPackage);
        }
    }

    private void completeOverlayChallenge() {
        int sessionLimit = overlayRule == null ? AppRule.DEFAULT_SESSION_LIMIT_MINUTES : Math.max(1, overlayRule.sessionLimitMinutes);
        int firstMinutes = Math.min(5, sessionLimit);
        overlayFiveMinuteButton.setEnabled(true);
        overlayFiveMinuteButton.setText("允许 " + firstMinutes + " 分钟");
        overlayFiveMinuteButton.setOnClickListener(v -> continueFromOverlay(firstMinutes));

        if (sessionLimit > firstMinutes) {
            overlayTenMinuteButton.setVisibility(View.VISIBLE);
            overlayTenMinuteButton.setEnabled(true);
            overlayTenMinuteButton.setText("允许 " + sessionLimit + " 分钟");
            overlayTenMinuteButton.setOnClickListener(v -> continueFromOverlay(sessionLimit));
        } else {
            overlayTenMinuteButton.setVisibility(View.GONE);
        }
        overlayBreathView.setText("现在再决定一次：你真的要打开它吗？");
        DiagnosticLogger.log(this, "challenge", "overlay challenge complete package=" + overlayTargetPackage
                + " sessionLimitMinutes=" + sessionLimit);
    }

    private void continueFromOverlay(int minutes) {
        String packageName = overlayTargetPackage;
        if (overlayRule != null) {
            minutes = Math.min(minutes, Math.max(1, overlayRule.sessionLimitMinutes));
        }
        DiagnosticLogger.log(this, "challenge", "overlay continue package=" + packageName + " minutes=" + minutes);
        RuleStore.grantPassthrough(this, packageName, minutes);
        schedulePassthroughExpiryCheck(packageName);
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
        if (overlayUnhide != null) {
            handler.removeCallbacks(overlayUnhide);
            overlayUnhide = null;
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
        overlayActionArea = null;
        overlayActionButton = null;
        overlayConfirmInput = null;
        overlayConfirmButton = null;
        overlayRule = null;
        overlayTargetPackage = null;
        overlayRemaining = 0;
        overlayTapCount = 0;
        overlayHideCount = 0;
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
        return AppCatalog.loadLabel(this, packageName);
    }

    private void schedulePassthroughExpiryCheck(String packageName) {
        long untilMillis = RuleStore.getPassthroughUntilMillis(this, packageName);
        if (untilMillis <= 0L) {
            return;
        }

        long delayMillis = Math.max(1000L, untilMillis - System.currentTimeMillis() + 500L);
        if (passthroughExpiryCheck != null) {
            handler.removeCallbacks(passthroughExpiryCheck);
        }
        passthroughExpiryCheck = () -> {
            DiagnosticLogger.log(this, "media", "passthrough expiry check package=" + packageName);
            RuleStore.clearExpiredPassthrough(this);
            BackgroundMediaBlocker.enforce(this, "passthrough expired package=" + packageName);
        };
        handler.postDelayed(passthroughExpiryCheck, delayMillis);
        DiagnosticLogger.log(this, "media", "scheduled passthrough expiry check package=" + packageName + " delayMillis=" + delayMillis);
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
