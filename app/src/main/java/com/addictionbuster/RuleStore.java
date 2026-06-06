package com.addictionbuster;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class RuleStore {
    private static final String PREFS_NAME = "addiction_buster_rules";
    private static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    private static final String KEY_PASSTHROUGH_PACKAGE = "passthrough_package";
    private static final String KEY_CHALLENGE_PACKAGE = "challenge_package";

    private RuleStore() {
    }

    static Set<String> getBlockedPackages(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_BLOCKED_PACKAGES, Collections.emptySet());
        return new HashSet<>(stored);
    }

    static void saveBlockedPackages(Context context, Set<String> packages) {
        prefs(context)
                .edit()
                .putStringSet(KEY_BLOCKED_PACKAGES, new HashSet<>(packages))
                .apply();
        DiagnosticLogger.log(context, "rule", "saved blocked packages=" + packages);
    }

    static boolean isBlocked(Context context, String packageName) {
        return getBlockedPackages(context).contains(packageName);
    }

    static void grantPassthrough(Context context, String packageName) {
        prefs(context)
                .edit()
                .putString(KEY_PASSTHROUGH_PACKAGE, packageName)
                .remove(KEY_CHALLENGE_PACKAGE)
                .apply();
        DiagnosticLogger.log(context, "rule", "grant passthrough package=" + packageName);
    }

    static boolean hasPassthrough(Context context, String packageName) {
        return packageName.equals(prefs(context).getString(KEY_PASSTHROUGH_PACKAGE, null));
    }

    static void clearPassthrough(Context context) {
        prefs(context).edit().remove(KEY_PASSTHROUGH_PACKAGE).apply();
        DiagnosticLogger.log(context, "rule", "clear passthrough");
    }

    static void clearPassthroughIfDifferent(Context context, String packageName) {
        String allowed = prefs(context).getString(KEY_PASSTHROUGH_PACKAGE, null);
        if (allowed != null && !allowed.equals(packageName)) {
            DiagnosticLogger.log(context, "rule", "clear passthrough because foreground=" + packageName + " allowed=" + allowed);
            clearPassthrough(context);
        }
    }

    static String getPassthroughPackage(Context context) {
        return prefs(context).getString(KEY_PASSTHROUGH_PACKAGE, null);
    }

    static String getChallengePackage(Context context) {
        return prefs(context).getString(KEY_CHALLENGE_PACKAGE, null);
    }

    static void setChallengePackage(Context context, String packageName) {
        prefs(context).edit().putString(KEY_CHALLENGE_PACKAGE, packageName).apply();
        DiagnosticLogger.log(context, "rule", "set active challenge package=" + packageName);
    }

    static void clearChallengePackage(Context context) {
        prefs(context).edit().remove(KEY_CHALLENGE_PACKAGE).apply();
        DiagnosticLogger.log(context, "rule", "clear active challenge package");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
