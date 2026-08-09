package com.addictionbuster;

import android.content.Context;

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

    public String displayName(Context context) {
        switch (this) {
            case GUGA:
                return context.getString(R.string.mascot_profile_guga);
            case DORO:
                return context.getString(R.string.mascot_profile_doro);
            case CUSTOM:
                return context.getString(R.string.mascot_profile_custom);
            case NONE:
            default:
                return context.getString(R.string.mascot_profile_none);
        }
    }

    public String description(Context context) {
        switch (this) {
            case GUGA:
                return context.getString(R.string.mascot_profile_guga_desc);
            case DORO:
                return context.getString(R.string.mascot_profile_doro_desc);
            case CUSTOM:
                return context.getString(R.string.mascot_profile_custom_desc);
            case NONE:
            default:
                return context.getString(R.string.mascot_profile_none_desc);
        }
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
