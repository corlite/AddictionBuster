package com.addictionbuster;

public final class AppRule {
    static final int DEFAULT_DAILY_QUOTA_MINUTES = 30;
    static final int DEFAULT_SESSION_LIMIT_MINUTES = 10;
    static final int DEFAULT_WAIT_SECONDS = 15;
    static final int DEFAULT_REQUIRED_TAPS = 0;
    static final int DEFAULT_HIDDEN_COUNT = 0;
    static final int DEFAULT_HIDDEN_SECONDS = 1;
    static final String DEFAULT_CONFIRM_TEXT = "";

    final int dailyQuotaMinutes;
    final int sessionLimitMinutes;
    final int waitSeconds;
    final int requiredTaps;
    final int hiddenCount;
    final int hiddenSeconds;
    final String confirmText;

    AppRule(
            int dailyQuotaMinutes,
            int sessionLimitMinutes,
            int waitSeconds,
            int requiredTaps,
            int hiddenCount,
            int hiddenSeconds,
            String confirmText
    ) {
        this.dailyQuotaMinutes = dailyQuotaMinutes;
        this.sessionLimitMinutes = sessionLimitMinutes;
        this.waitSeconds = waitSeconds;
        this.requiredTaps = requiredTaps;
        this.hiddenCount = hiddenCount;
        this.hiddenSeconds = hiddenSeconds;
        this.confirmText = confirmText == null ? "" : confirmText.trim();
    }

    static AppRule defaults() {
        return new AppRule(
                DEFAULT_DAILY_QUOTA_MINUTES,
                DEFAULT_SESSION_LIMIT_MINUTES,
                DEFAULT_WAIT_SECONDS,
                DEFAULT_REQUIRED_TAPS,
                DEFAULT_HIDDEN_COUNT,
                DEFAULT_HIDDEN_SECONDS,
                DEFAULT_CONFIRM_TEXT
        );
    }
}
