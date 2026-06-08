package com.addictionbuster.enforcement.executor

import android.content.Context
import android.provider.Settings

class AndroidOverlayPermissionChecker(
    private val context: Context
) : OverlayPermissionChecker {
    override fun canShowOverlay(): Boolean =
        Settings.canDrawOverlays(context.applicationContext)
}
