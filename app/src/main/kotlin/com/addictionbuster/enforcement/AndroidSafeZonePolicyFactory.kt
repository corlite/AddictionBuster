package com.addictionbuster.enforcement

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

object AndroidSafeZonePolicyFactory {
    private val defaultSystemUiPackages = setOf(
        "android",
        "com.android.systemui"
    )

    private val defaultPhonePackages = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.miui.securitycenter",
        "com.android.phone",
        "com.android.server.telecom"
    )

    private val defaultSettingsPackages = setOf(
        "com.android.settings"
    )

    fun create(context: Context): SafeZonePolicy {
        val applicationContext = context.applicationContext
        val launcherPackages = resolveLauncherPackages(applicationContext)
        val inputMethodPackages = resolveInputMethodPackages(applicationContext)
        return SafeZonePolicy(
            selfPackageName = applicationContext.packageName,
            launcherPackages = launcherPackages,
            systemUiPackages = defaultSystemUiPackages,
            phonePackages = defaultPhonePackages,
            inputMethodPackages = inputMethodPackages,
            systemSettingsPackages = defaultSettingsPackages,
            permissionSettingsPackages = defaultSettingsPackages
        )
    }

    private fun resolveLauncherPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it.isNotBlank() }
            .toSet()
        if (resolved.isEmpty()) {
            throw InvalidEnforcementContextException("unable to resolve launcher packages")
        }
        return resolved
    }

    private fun resolveInputMethodPackages(context: Context): Set<String> {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptySet()
        return manager.enabledInputMethodList
            .mapNotNull { it.packageName }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun overlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context.applicationContext)
}
