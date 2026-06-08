package com.addictionbuster;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public class BusterNotificationListenerService extends NotificationListenerService {
    private MediaSessionManager mediaSessionManager;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionListener;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        DiagnosticLogger.log(this, "media", "notification listener connected");
        if (V2RuntimeMode.isEnabled(this)) {
            DiagnosticLogger.log(this, "media", "legacy media blocker disabled because v2 enforcement is enabled");
            return;
        }
        registerMediaSessionListener();
        BackgroundMediaBlocker.enforce(this, "notification listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        if (V2RuntimeMode.isEnabled(this)) {
            DiagnosticLogger.log(this, "media", "notification posted ignored by legacy media blocker because v2 enforcement is enabled");
            return;
        }
        DiagnosticLogger.log(this, "media", "notification posted package=" + sbn.getPackageName());
        BackgroundMediaBlocker.enforce(this, "notification posted package=" + sbn.getPackageName());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        if (V2RuntimeMode.isEnabled(this)) {
            DiagnosticLogger.log(this, "media", "notification removed ignored by legacy media blocker because v2 enforcement is enabled");
            return;
        }
        DiagnosticLogger.log(this, "media", "notification removed package=" + sbn.getPackageName());
        BackgroundMediaBlocker.enforce(this, "notification removed package=" + sbn.getPackageName());
    }

    @Override
    public void onListenerDisconnected() {
        DiagnosticLogger.log(this, "media", "notification listener disconnected");
        unregisterMediaSessionListener();
        super.onListenerDisconnected();
    }

    private void registerMediaSessionListener() {
        if (sessionListener != null) {
            return;
        }

        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (mediaSessionManager == null) {
            DiagnosticLogger.log(this, "media", "media session manager unavailable in notification listener");
            return;
        }

        sessionListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(List<MediaController> controllers) {
                DiagnosticLogger.log(BusterNotificationListenerService.this, "media", "active media sessions changed count=" + (controllers == null ? 0 : controllers.size()));
                BackgroundMediaBlocker.enforce(BusterNotificationListenerService.this, "active sessions changed");
            }
        };

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionListener,
                    new ComponentName(this, BusterNotificationListenerService.class)
            );
            DiagnosticLogger.log(this, "media", "registered active media session listener");
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "media", "failed to register active media session listener error=" + exception);
            sessionListener = null;
        }
    }

    private void unregisterMediaSessionListener() {
        if (mediaSessionManager == null || sessionListener == null) {
            return;
        }
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
            DiagnosticLogger.log(this, "media", "unregistered active media session listener");
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "media", "failed to unregister active media session listener error=" + exception);
        }
        sessionListener = null;
    }
}
