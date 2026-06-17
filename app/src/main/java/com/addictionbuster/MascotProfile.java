package com.addictionbuster;

public enum MascotProfile {
    NONE("关闭", "不显示角色，也不播放角色语音"),
    GUGA("咕嘎", "预留咕嘎图标和语音坑位"),
    DORO("Doro", "预留 Doro 图标和语音坑位"),
    CUSTOM("自定义", "使用你导入的自定义图标和语音");

    final String displayName;
    final String description;

    MascotProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static MascotProfile fromStoredValue(String value) {
        if (value == null) {
            return NONE;
        }
        try {
            return MascotProfile.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
