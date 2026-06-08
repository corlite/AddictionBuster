package com.addictionbuster.enforcement.executor

import android.accessibilityservice.AccessibilityService

class AccessibilityHomeActionPerformer(
    private val service: AccessibilityService
) : HomeActionPerformer {
    override fun performHome(): Boolean =
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
}
