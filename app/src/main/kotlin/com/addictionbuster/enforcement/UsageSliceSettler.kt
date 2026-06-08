package com.addictionbuster.enforcement

class UsageSliceSettler {
    fun settle(previousContext: EnforcementContext, nowMillis: Long): UsageCommit {
        if (nowMillis < previousContext.sliceStartedAtMillis) {
            throw InvalidEnforcementContextException("nowMillis is before sliceStartedAtMillis")
        }
        val durationMillis = nowMillis - previousContext.sliceStartedAtMillis
        val appCounted = shouldCountApp(previousContext)
        val phoneCounted = shouldCountPhone(previousContext)
        return UsageCommit(
            sliceDurationMillis = durationMillis,
            appUsageMillis = if (appCounted) durationMillis else 0L,
            phoneUsageMillis = if (phoneCounted) durationMillis else 0L,
            appCounted = appCounted,
            phoneCounted = phoneCounted
        )
    }

    fun shouldCountApp(context: EnforcementContext): Boolean {
        if (context.activeOverlayType != OverlayType.NONE) return false
        if (context.screenState != ScreenState.UNLOCKED) return false
        val app = context.foregroundApp
        if (context.safeZonePolicy.isSafe(app)) return false
        if (app.isSystem || app.isLauncher || app.isEmergencyAllowed) return false
        val appPolicy = context.ruleSnapshot.requireAppPolicyFor(app.identityKey)
        return appPolicy.enabled
    }

    fun shouldCountPhone(context: EnforcementContext): Boolean {
        if (context.activeOverlayType != OverlayType.NONE) return false
        if (context.screenState != ScreenState.UNLOCKED) return false
        val app = context.foregroundApp
        if (context.safeZonePolicy.isSafe(app)) return false
        if (app.isSystem || app.isLauncher || app.isEmergencyAllowed) return false
        if (app.identityKey in context.ruleSnapshot.globalPolicy.countWhitelistIdentities) return false
        val appPolicy = context.ruleSnapshot.requireAppPolicyFor(app.identityKey)
        return appPolicy.countTowardsPhoneUsage
    }
}

data class UsageCommit(
    val sliceDurationMillis: Long,
    val appUsageMillis: Long,
    val phoneUsageMillis: Long,
    val appCounted: Boolean,
    val phoneCounted: Boolean
) {
    init {
        if (sliceDurationMillis < 0L) {
            throw InvalidEnforcementContextException("sliceDurationMillis must be >= 0")
        }
        if (appUsageMillis < 0L) {
            throw InvalidEnforcementContextException("appUsageMillis must be >= 0")
        }
        if (phoneUsageMillis < 0L) {
            throw InvalidEnforcementContextException("phoneUsageMillis must be >= 0")
        }
    }
}
