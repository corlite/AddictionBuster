package com.addictionbuster.enforcement.health

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.addictionbuster.enforcement.AndroidSafeZonePolicyFactory
import com.addictionbuster.enforcement.SystemHealthState

class AndroidSystemHealthReader(
    private val context: Context,
    private val accessibilityServiceComponent: ComponentName
) {
    fun read(
        notificationListenerConnected: Boolean,
        foregroundServiceRunning: Boolean
    ): SystemHealthState {
        val appContext = context.applicationContext
        return SystemHealthState(
            accessibilityConnected = isAccessibilityServiceEnabled(appContext),
            overlayPermissionGranted = AndroidSafeZonePolicyFactory.overlayPermissionGranted(appContext),
            notificationListenerConnected = notificationListenerConnected,
            batteryOptimizationsIgnored = isIgnoringBatteryOptimizations(appContext),
            foregroundServiceRunning = foregroundServiceRunning
        )
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = accessibilityServiceComponent.flattenToString()
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            if (service.equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
