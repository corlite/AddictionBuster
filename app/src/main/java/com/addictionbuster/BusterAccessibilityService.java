package com.addictionbuster;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

public class BusterAccessibilityService extends AccessibilityService {
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_TARGET_LABEL = "target_label";
    private static final int WINDOW_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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

        DiagnosticLogger.log(
                this,
                "event",
                "window type=" + event.getEventType()
                        + " package=" + packageName
                        + " class=" + className
                        + " blocked=" + blocked
                        + " passthrough=" + passthrough
                        + " activeChallenge=" + activeChallenge
        );

        if (getPackageName().equals(packageName)) {
            DiagnosticLogger.log(this, "service", "ignore own package=" + packageName);
            return;
        }

        RuleStore.clearPassthroughIfDifferent(this, packageName);

        if (!blocked) {
            DiagnosticLogger.log(this, "service", "allow because package is not blocked: " + packageName);
            return;
        }
        if (passthrough) {
            DiagnosticLogger.log(this, "service", "allow because passthrough is active: " + packageName);
            return;
        }

        if (packageName.equals(activeChallenge)) {
            DiagnosticLogger.log(this, "service", "challenge already active for package=" + packageName);
            return;
        }

        launchChallenge(packageName);
    }

    @Override
    public void onInterrupt() {
        DiagnosticLogger.log(this, "service", "accessibility service interrupted");
    }

    private void launchChallenge(String packageName) {
        RuleStore.setChallengePackage(this, packageName);
        String label = loadLabel(packageName);

        Intent intent = new Intent(this, ChallengeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_TARGET_PACKAGE, packageName);
        intent.putExtra(EXTRA_TARGET_LABEL, label);
        try {
            DiagnosticLogger.log(this, "challenge", "launch challenge package=" + packageName + " label=" + label);
            startActivity(intent);
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "challenge", "failed to launch challenge package=" + packageName + " error=" + exception);
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
}
