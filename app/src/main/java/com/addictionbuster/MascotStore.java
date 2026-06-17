package com.addictionbuster;

import android.content.Context;
import android.content.SharedPreferences;

public final class MascotStore {
    private static final String PREFS = "mascot_profile";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_VOICE_ENABLED = "voice_enabled";
    private static final String KEY_VOLUME_PERCENT = "volume_percent";
    private static final String KEY_ICON_PREFIX = "icon_uri_";
    private static final String KEY_VOICE_PREFIX = "voice_uri_";

    private MascotStore() {
    }

    public static MascotProfile getProfile(Context context) {
        return MascotProfile.fromStoredValue(prefs(context).getString(KEY_PROFILE, MascotProfile.NONE.name()));
    }

    public static void saveProfile(Context context, MascotProfile profile) {
        prefs(context).edit().putString(KEY_PROFILE, profile.name()).apply();
    }

    public static boolean isVoiceEnabled(Context context) {
        return prefs(context).getBoolean(KEY_VOICE_ENABLED, false);
    }

    public static void setVoiceEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply();
    }

    public static int getVolumePercent(Context context) {
        return clampVolumePercent(prefs(context).getInt(KEY_VOLUME_PERCENT, 60));
    }

    public static void setVolumePercent(Context context, int percent) {
        prefs(context).edit().putInt(KEY_VOLUME_PERCENT, clampVolumePercent(percent)).apply();
    }

    public static String getIconUri(Context context, MascotProfile profile) {
        return prefs(context).getString(KEY_ICON_PREFIX + profile.name(), "");
    }

    public static void setIconUri(Context context, MascotProfile profile, String uri) {
        prefs(context).edit().putString(KEY_ICON_PREFIX + profile.name(), clean(uri)).apply();
    }

    public static String getVoiceUri(Context context, MascotProfile profile) {
        return prefs(context).getString(KEY_VOICE_PREFIX + profile.name(), "");
    }

    public static void setVoiceUri(Context context, MascotProfile profile, String uri) {
        prefs(context).edit().putString(KEY_VOICE_PREFIX + profile.name(), clean(uri)).apply();
    }

    public static String getCurrentIconUri(Context context) {
        MascotProfile profile = getProfile(context);
        return profile == MascotProfile.NONE ? "" : getIconUri(context, profile);
    }

    public static String getCurrentVoiceUri(Context context) {
        MascotProfile profile = getProfile(context);
        return profile == MascotProfile.NONE ? "" : getVoiceUri(context, profile);
    }

    public static int clampVolumePercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
