package com.addictionbuster;

public enum MascotVoiceSlot {
    BLOCK_APPEARED("拦截出现", "目标 App 被拦截或应用时长到时播放"),
    CHALLENGE_PASSED("挑战通过", "完成挑战并获得放行时播放"),
    PHONE_LIMIT_REACHED("手机时长到", "手机每日/本次时长到达限制时播放"),
    PERMISSION_ISSUE("权限异常", "无障碍、悬浮窗或前台服务异常时播放");

    private final String displayName;
    private final String description;

    MascotVoiceSlot(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
