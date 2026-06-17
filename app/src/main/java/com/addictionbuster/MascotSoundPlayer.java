package com.addictionbuster;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import com.addictionbuster.enforcement.EnforcementAction;

public final class MascotSoundPlayer {
    private MascotSoundPlayer() {
    }

    public static void playForAction(Context context, EnforcementAction action) {
        if (action == EnforcementAction.SHOW_PHONE_LIMIT_BLOCK) {
            play(context, MascotVoiceSlot.PHONE_LIMIT_REACHED);
            return;
        }
        if (action == EnforcementAction.SHOW_APP_CHALLENGE
                || action == EnforcementAction.SHOW_APP_LIMIT_BLOCK) {
            play(context, MascotVoiceSlot.BLOCK_APPEARED);
            return;
        }
        if (action == EnforcementAction.FAIL_CLOSED_HOME
                || action == EnforcementAction.FAIL_CLOSED_GLOBAL) {
            play(context, MascotVoiceSlot.PERMISSION_ISSUE);
        }
    }

    public static void playChallengePassed(Context context) {
        play(context, MascotVoiceSlot.CHALLENGE_PASSED);
    }

    public static void playPermissionIssue(Context context) {
        play(context, MascotVoiceSlot.PERMISSION_ISSUE);
    }

    public static boolean canPlay(Context context, MascotVoiceSlot slot) {
        return MascotStore.isVoiceEnabled(context)
                && !MascotStore.getCurrentVoiceUri(context, slot).isEmpty();
    }

    public static void play(Context context, MascotVoiceSlot slot) {
        if (!MascotStore.isVoiceEnabled(context)) {
            return;
        }
        String value = MascotStore.getCurrentVoiceUri(context, slot);
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
