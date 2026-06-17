package com.addictionbuster;

public enum MascotVoiceSlot {
    BLOCK_APPEARED("拦截出现", "目标 App 被拦截或应用时长到时播放"),
    CHALLENGE_PASSED("挑战通过", "完成挑战并获得放行时播放"),
    PHONE_LIMIT_REACHED("手机时长到", "手机每日/本次时长到达限制时播放"),
    PERMISSION_ISSUE("权限异常", "无障碍、悬浮窗或前台服务异常时播放"),
    CONTROL_APPS("管控应用", "主页管控应用区域出现时播放"),
    ACTIVE_APPS("已管控应用", "进入已管控应用列表时播放"),
    ADD_APPS("添加应用", "进入添加管控应用页面时播放"),
    TODAY_REPORT("今日报告", "进入今日报告页面时播放"),
    APP_RULE("拦截规则", "进入单 App 拦截规则页时播放");

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
