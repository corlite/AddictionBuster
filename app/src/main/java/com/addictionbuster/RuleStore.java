package com.addictionbuster;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class RuleStore {
    private static final String PREFS_NAME = "addiction_buster_rules";
    private static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    private static final String KEY_PHONE_WHITELIST_PACKAGES = "phone_whitelist_packages";
    private static final String KEY_PHONE_DAILY_LIMIT_MINUTES = "phone_daily_limit_minutes";
    private static final String KEY_PHONE_SESSION_LIMIT_MINUTES = "phone_session_limit_minutes";
    private static final String KEY_PASSTHROUGH_PACKAGE = "passthrough_package";
    private static final String KEY_PASSTHROUGH_UNTIL_MILLIS = "passthrough_until_millis";
    private static final String KEY_CHALLENGE_PACKAGE = "challenge_package";
    private static final String RULE_PREFIX = "app_rule.";
    private static final String FIELD_DAILY_QUOTA_MINUTES = ".daily_quota_minutes";
    private static final String FIELD_SESSION_LIMIT_MINUTES = ".session_limit_minutes";
    private static final String FIELD_WAIT_SECONDS = ".wait_seconds";
    private static final String FIELD_REQUIRED_TAPS = ".required_taps";
    private static final String FIELD_HIDDEN_COUNT = ".hidden_count";
    private static final String FIELD_HIDDEN_SECONDS = ".hidden_seconds";
    private static final String FIELD_CONFIRM_TEXT = ".confirm_text";
    private static final String USAGE_PREFIX = "usage.";
    private static final String FIELD_USAGE_DATE = ".date";
    private static final String FIELD_USAGE_SECONDS = ".seconds";
    private static final String KEY_PHONE_USAGE_DATE = "phone_usage.date";
    private static final String KEY_PHONE_USAGE_SECONDS = "phone_usage.seconds";
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

    static int getPhoneDailyLimitMinutes(Context context) {
        return Math.max(0, prefs(context).getInt(KEY_PHONE_DAILY_LIMIT_MINUTES, 0));
    }

    static int getPhoneSessionLimitMinutes(Context context) {
        return Math.max(0, prefs(context).getInt(KEY_PHONE_SESSION_LIMIT_MINUTES, 0));
    }

    static void savePhoneLimits(Context context, int dailyLimitMinutes, int sessionLimitMinutes) {
        prefs(context)
                .edit()
                .putInt(KEY_PHONE_DAILY_LIMIT_MINUTES, clamp(dailyLimitMinutes, 0, 1440))
                .putInt(KEY_PHONE_SESSION_LIMIT_MINUTES, clamp(sessionLimitMinutes, 0, 240))
                .apply();
        DiagnosticLogger.log(context, "rule", "saved phone limits dailyMinutes=" + dailyLimitMinutes
                + " sessionMinutes=" + sessionLimitMinutes);
    }

    static boolean hasPhoneLimits(Context context) {
        return getPhoneDailyLimitMinutes(context) > 0 || getPhoneSessionLimitMinutes(context) > 0;
    }

    static Set<String> getPhoneWhitelistPackages(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_PHONE_WHITELIST_PACKAGES, Collections.emptySet());
        return new HashSet<>(stored);
    }

    static void savePhoneWhitelistPackages(Context context, Set<String> packages) {
        prefs(context)
                .edit()
                .putStringSet(KEY_PHONE_WHITELIST_PACKAGES, new HashSet<>(packages))
                .apply();
        DiagnosticLogger.log(context, "rule", "saved phone whitelist packages=" + packages);
    }

    static boolean isPhoneWhitelist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        if (context.getPackageName().equals(packageName)) {
            return true;
        }
        return defaultPhoneWhitelistPackages().contains(packageName)
                || getPhoneWhitelistPackages(context).contains(packageName);
    }

    static AppRule getAppRule(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        AppRule defaults = AppRule.defaults();
        return new AppRule(
                preferences.getInt(ruleKey(packageName, FIELD_DAILY_QUOTA_MINUTES), defaults.dailyQuotaMinutes),
                preferences.getInt(ruleKey(packageName, FIELD_SESSION_LIMIT_MINUTES), defaults.sessionLimitMinutes),
                preferences.getInt(ruleKey(packageName, FIELD_WAIT_SECONDS), defaults.waitSeconds),
                preferences.getInt(ruleKey(packageName, FIELD_REQUIRED_TAPS), defaults.requiredTaps),
                preferences.getInt(ruleKey(packageName, FIELD_HIDDEN_COUNT), defaults.hiddenCount),
                preferences.getInt(ruleKey(packageName, FIELD_HIDDEN_SECONDS), defaults.hiddenSeconds),
                preferences.getString(ruleKey(packageName, FIELD_CONFIRM_TEXT), defaults.confirmText)
        );
    }

    static void saveAppRule(Context context, String packageName, AppRule rule) {
        prefs(context)
                .edit()
                .putInt(ruleKey(packageName, FIELD_DAILY_QUOTA_MINUTES), clamp(rule.dailyQuotaMinutes, 0, 1440))
                .putInt(ruleKey(packageName, FIELD_SESSION_LIMIT_MINUTES), clamp(rule.sessionLimitMinutes, 1, 240))
                .putInt(ruleKey(packageName, FIELD_WAIT_SECONDS), clamp(rule.waitSeconds, 0, 300))
                .putInt(ruleKey(packageName, FIELD_REQUIRED_TAPS), clamp(rule.requiredTaps, 0, 30))
                .putInt(ruleKey(packageName, FIELD_HIDDEN_COUNT), clamp(rule.hiddenCount, 0, 20))
                .putInt(ruleKey(packageName, FIELD_HIDDEN_SECONDS), clamp(rule.hiddenSeconds, 1, 20))
                .putString(ruleKey(packageName, FIELD_CONFIRM_TEXT), rule.confirmText)
                .apply();
        DiagnosticLogger.log(context, "rule", "saved app rule package=" + packageName
                + " dailyQuotaMinutes=" + rule.dailyQuotaMinutes
                + " sessionLimitMinutes=" + rule.sessionLimitMinutes
                + " waitSeconds=" + rule.waitSeconds
                + " requiredTaps=" + rule.requiredTaps
                + " hiddenCount=" + rule.hiddenCount
                + " hiddenSeconds=" + rule.hiddenSeconds
                + " confirmTextLength=" + rule.confirmText.length());
    }

    static void clearAppRule(Context context, String packageName) {
        prefs(context)
                .edit()
                .remove(ruleKey(packageName, FIELD_DAILY_QUOTA_MINUTES))
                .remove(ruleKey(packageName, FIELD_SESSION_LIMIT_MINUTES))
                .remove(ruleKey(packageName, FIELD_WAIT_SECONDS))
                .remove(ruleKey(packageName, FIELD_REQUIRED_TAPS))
                .remove(ruleKey(packageName, FIELD_HIDDEN_COUNT))
                .remove(ruleKey(packageName, FIELD_HIDDEN_SECONDS))
                .remove(ruleKey(packageName, FIELD_CONFIRM_TEXT))
                .apply();
        DiagnosticLogger.log(context, "rule", "cleared app rule package=" + packageName);
    }

    static long getDailyUsedSeconds(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        String today = todayKey();
        String storedDate = preferences.getString(usageKey(packageName, FIELD_USAGE_DATE), "");
        if (!today.equals(storedDate)) {
            preferences
                    .edit()
                    .putString(usageKey(packageName, FIELD_USAGE_DATE), today)
                    .putLong(usageKey(packageName, FIELD_USAGE_SECONDS), 0L)
                    .apply();
            return 0L;
        }
        return Math.max(0L, preferences.getLong(usageKey(packageName, FIELD_USAGE_SECONDS), 0L));
    }

    static long getPhoneDailyUsedSeconds(Context context) {
        SharedPreferences preferences = prefs(context);
        String today = todayKey();
        String storedDate = preferences.getString(KEY_PHONE_USAGE_DATE, "");
        if (!today.equals(storedDate)) {
            preferences
                    .edit()
                    .putString(KEY_PHONE_USAGE_DATE, today)
                    .putLong(KEY_PHONE_USAGE_SECONDS, 0L)
                    .apply();
            return 0L;
        }
        return Math.max(0L, preferences.getLong(KEY_PHONE_USAGE_SECONDS, 0L));
    }

    static long getPhoneDailyRemainingSeconds(Context context) {
        int dailyLimitMinutes = getPhoneDailyLimitMinutes(context);
        if (dailyLimitMinutes <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, dailyLimitMinutes * 60L - getPhoneDailyUsedSeconds(context));
    }

    static long getDailyRemainingSeconds(Context context, String packageName, AppRule rule) {
        if (rule.dailyQuotaMinutes <= 0) {
            return Long.MAX_VALUE;
        }
        long quotaSeconds = rule.dailyQuotaMinutes * 60L;
        return Math.max(0L, quotaSeconds - getDailyUsedSeconds(context, packageName));
    }

    static void addDailyUsageMillis(Context context, String packageName, long millis, String reason) {
        if (millis <= 0L) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        String today = todayKey();
        String storedDate = preferences.getString(usageKey(packageName, FIELD_USAGE_DATE), "");
        long currentSeconds = today.equals(storedDate)
                ? Math.max(0L, preferences.getLong(usageKey(packageName, FIELD_USAGE_SECONDS), 0L))
                : 0L;
        long addSeconds = Math.max(1L, millis / 1000L);
        long nextSeconds = currentSeconds + addSeconds;
        preferences
                .edit()
                .putString(usageKey(packageName, FIELD_USAGE_DATE), today)
                .putLong(usageKey(packageName, FIELD_USAGE_SECONDS), nextSeconds)
                .apply();
        DiagnosticLogger.log(context, "usage", "add usage package=" + packageName
                + " addSeconds=" + addSeconds
                + " totalSeconds=" + nextSeconds
                + " reason=" + reason);
    }

    static long addPhoneUsageMillis(Context context, long millis, String packageName, String reason) {
        if (millis <= 0L) {
            return getPhoneDailyUsedSeconds(context);
        }
        SharedPreferences preferences = prefs(context);
        String today = todayKey();
        String storedDate = preferences.getString(KEY_PHONE_USAGE_DATE, "");
        long currentSeconds = today.equals(storedDate)
                ? Math.max(0L, preferences.getLong(KEY_PHONE_USAGE_SECONDS, 0L))
                : 0L;
        long addSeconds = Math.max(1L, millis / 1000L);
        long nextSeconds = currentSeconds + addSeconds;
        preferences
                .edit()
                .putString(KEY_PHONE_USAGE_DATE, today)
                .putLong(KEY_PHONE_USAGE_SECONDS, nextSeconds)
                .apply();
        DiagnosticLogger.log(context, "usage", "add phone usage package=" + packageName
                + " addSeconds=" + addSeconds
                + " totalSeconds=" + nextSeconds
                + " reason=" + reason);
        return nextSeconds;
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

    static long getPassthroughUntilMillis(Context context, String packageName) {
        if (!hasPassthrough(context, packageName)) {
            return 0L;
        }
        return prefs(context).getLong(KEY_PASSTHROUGH_UNTIL_MILLIS, 0L);
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

    private static String ruleKey(String packageName, String field) {
        return RULE_PREFIX + packageName + field;
    }

    private static String usageKey(String packageName, String field) {
        return USAGE_PREFIX + packageName + field;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> defaultPhoneWhitelistPackages() {
        Set<String> packages = new HashSet<>();
        packages.add("android");
        packages.add("com.android.systemui");
        packages.add("com.android.settings");
        packages.add("com.google.android.settings");
        packages.add("com.android.phone");
        packages.add("com.google.android.dialer");
        packages.add("com.android.dialer");
        packages.add("com.android.contacts");
        packages.add("com.google.android.contacts");
        packages.add("com.android.mms");
        packages.add("com.google.android.apps.messaging");
        packages.add("com.google.android.inputmethod.latin");
        packages.add("com.android.inputmethod.latin");
        packages.add("com.miui.home");
        packages.add("com.huawei.android.launcher");
        packages.add("com.oppo.launcher");
        packages.add("com.vivo.launcher");
        packages.add("com.android.launcher");
        packages.add("com.android.launcher3");
        return packages;
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }
}
