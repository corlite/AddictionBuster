package com.addictionbuster;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

final class BackgroundMediaBlocker {
    private BackgroundMediaBlocker() {
    }

    static void enforce(Context context, String reason) {
        MediaSessionManager mediaSessionManager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (mediaSessionManager == null) {
            DiagnosticLogger.log(context, "media", "media session manager unavailable reason=" + reason);
            return;
        }

        ComponentName listener = new ComponentName(context, BusterNotificationListenerService.class);
        List<MediaController> controllers;
        try {
            controllers = mediaSessionManager.getActiveSessions(listener);
        } catch (SecurityException exception) {
            DiagnosticLogger.log(context, "media", "notification listener not enabled; cannot inspect media sessions reason=" + reason);
            return;
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(context, "media", "failed to inspect media sessions reason=" + reason + " error=" + exception);
            return;
        }

        for (MediaController controller : controllers) {
            String packageName = controller.getPackageName();
            if (!RuleStore.isBlocked(context, packageName)) {
                continue;
            }

            if (RuleStore.hasPassthrough(context, packageName)) {
                DiagnosticLogger.log(context, "media", "allow blocked media because passthrough package=" + packageName
                        + " remainingSeconds=" + RuleStore.getPassthroughRemainingSeconds(context, packageName));
                continue;
            }

            PlaybackState state = controller.getPlaybackState();
            int playbackState = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (playbackState == PlaybackState.STATE_PLAYING
                    || playbackState == PlaybackState.STATE_BUFFERING
                    || playbackState == PlaybackState.STATE_CONNECTING
                    || state == null) {
                try {
                    controller.getTransportControls().pause();
                    DiagnosticLogger.log(context, "media", "pause blocked media package=" + packageName + " state=" + playbackState + " reason=" + reason);
                } catch (RuntimeException exception) {
                    DiagnosticLogger.log(context, "media", "failed to pause blocked media package=" + packageName + " error=" + exception);
                }
            }
        }
    }
}
