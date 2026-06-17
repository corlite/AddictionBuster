package com.addictionbuster;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import com.addictionbuster.enforcement.EnforcementAction;

public final class MascotSoundPlayer {
    private MascotSoundPlayer() {
    }

    public static void playForAction(Context context, EnforcementAction action) {
        if (action == EnforcementAction.SHOW_APP_CHALLENGE
                || action == EnforcementAction.SHOW_APP_LIMIT_BLOCK
                || action == EnforcementAction.SHOW_PHONE_LIMIT_BLOCK
                || action == EnforcementAction.FAIL_CLOSED_HOME
                || action == EnforcementAction.FAIL_CLOSED_GLOBAL) {
            playCurrent(context);
        }
    }

    public static void playChallengePassed(Context context) {
        playCurrent(context);
    }

    public static void playPermissionIssue(Context context) {
        playCurrent(context);
    }

    public static boolean canPlayCurrent(Context context) {
        return MascotStore.isVoiceEnabled(context)
                && !MascotStore.getCurrentVoiceUri(context).isEmpty();
    }

    public static void playCurrent(Context context) {
        if (!MascotStore.isVoiceEnabled(context)) {
            return;
        }
        String value = MascotStore.getCurrentVoiceUri(context);
        if (value.isEmpty()) {
            return;
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(context.getApplicationContext(), Uri.parse(value));
            float volume = MascotStore.getVolumePercent(context) / 100f;
            player.setVolume(volume, volume);
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                return true;
            });
            player.prepare();
            player.start();
        } catch (RuntimeException | java.io.IOException exception) {
            player.release();
            DiagnosticLogger.log(context, "mascot", "voice play failed error=" + exception.getMessage());
        }
    }
}
