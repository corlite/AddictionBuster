package com.addictionbuster;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;

public class BusterAccessibilityService extends AccessibilityService {
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_TARGET_LABEL = "target_label";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence packageValue = event.getPackageName();
        if (packageValue == null) {
            return;
        }

        String packageName = packageValue.toString();
        if (getPackageName().equals(packageName)) {
            return;
        }

        RuleStore.clearPassthroughIfDifferent(this, packageName);

        if (!RuleStore.isBlocked(this, packageName)) {
            return;
        }
        if (RuleStore.hasPassthrough(this, packageName)) {
            return;
        }

        String activeChallenge = RuleStore.getChallengePackage(this);
        if (packageName.equals(activeChallenge)) {
            return;
        }

        launchChallenge(packageName);
    }

    @Override
    public void onInterrupt() {
    }

    private void launchChallenge(String packageName) {
        RuleStore.setChallengePackage(this, packageName);

        Intent intent = new Intent(this, ChallengeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_TARGET_PACKAGE, packageName);
        intent.putExtra(EXTRA_TARGET_LABEL, loadLabel(packageName));
        startActivity(intent);
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
