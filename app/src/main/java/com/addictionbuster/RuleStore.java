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
    private static final String KEY_PASSTHROUGH_UNTIL_MILLIS = "passthrough_until_millis";
    private static final String KEY_CHALLENGE_PACKAGE = "challenge_package";
    private static final long MINUTE_MILLIS = 60_000L;

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

    static void grantPassthrough(Context context, String packageName, int minutes) {
        long untilMillis = System.currentTimeMillis() + minutes * MINUTE_MILLIS;
        prefs(context)
                .edit()
                .putString(KEY_PASSTHROUGH_PACKAGE, packageName)
                .putLong(KEY_PASSTHROUGH_UNTIL_MILLIS, untilMillis)
                .remove(KEY_CHALLENGE_PACKAGE)
                .apply();
        DiagnosticLogger.log(context, "rule", "grant passthrough package=" + packageName + " minutes=" + minutes + " untilMillis=" + untilMillis);
    }

    static boolean hasPassthrough(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        String allowed = preferences.getString(KEY_PASSTHROUGH_PACKAGE, null);
        if (!packageName.equals(allowed)) {
            return false;
        }

        long untilMillis = preferences.getLong(KEY_PASSTHROUGH_UNTIL_MILLIS, 0L);
        if (untilMillis <= System.currentTimeMillis()) {
            DiagnosticLogger.log(context, "rule", "passthrough expired package=" + packageName + " untilMillis=" + untilMillis);
            clearPassthrough(context);
            return false;
        }
        return true;
    }

    static void clearPassthrough(Context context) {
        prefs(context)
                .edit()
                .remove(KEY_PASSTHROUGH_PACKAGE)
                .remove(KEY_PASSTHROUGH_UNTIL_MILLIS)
                .apply();
        DiagnosticLogger.log(context, "rule", "clear passthrough");
    }

    static void clearExpiredPassthrough(Context context) {
        String allowed = prefs(context).getString(KEY_PASSTHROUGH_PACKAGE, null);
        if (allowed != null) {
            hasPassthrough(context, allowed);
        }
    }

    static String getPassthroughPackage(Context context) {
        SharedPreferences preferences = prefs(context);
        String allowed = preferences.getString(KEY_PASSTHROUGH_PACKAGE, null);
        if (allowed == null) {
            return null;
        }
        if (!hasPassthrough(context, allowed)) {
            return null;
        }
        return allowed;
    }

    static long getPassthroughRemainingSeconds(Context context, String packageName) {
        if (!hasPassthrough(context, packageName)) {
            return 0L;
        }
        long untilMillis = prefs(context).getLong(KEY_PASSTHROUGH_UNTIL_MILLIS, 0L);
        return Math.max(0L, (untilMillis - System.currentTimeMillis()) / 1000L);
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
