package com.addictionbuster.enforcement.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.addictionbuster.enforcement.SystemHealthIssue
import com.addictionbuster.MascotSoundPlayer
import com.addictionbuster.R

class SystemHealthNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun notifyFatalIssues(issues: Set<SystemHealthIssue>) {
        if (issues.isEmpty()) return
        MascotSoundPlayer.playPermissionIssue(context)
        ensureChannel()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.system_health_notification_title))
            .setContentText(context.getString(R.string.system_health_notification_text))
            .setStyle(
                android.app.Notification.BigTextStyle()
                    .bigText(context.getString(R.string.system_health_notification_big_text, issues.joinToString()))
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.system_health_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.system_health_channel_description)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "enforcement_health"
        const val NOTIFICATION_ID = 6201
    }
}
