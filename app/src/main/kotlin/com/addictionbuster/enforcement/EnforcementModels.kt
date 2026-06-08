package com.addictionbuster.enforcement

enum class IdentityType {
    NORMAL,
    SYSTEM_CLONE,
    WORK_PROFILE,
    CLONE_CONTAINER,
    MANUAL_CLONE,
    UNKNOWN_IDENTITY
}

enum class ScreenState {
    ON,
    OFF,
    LOCKED,
    UNLOCKED
}

enum class OverlayType {
    NONE,
    APP_CHALLENGE,
    APP_LIMIT_BLOCK,
    PHONE_LIMIT_BLOCK,
    SLEEP_LOCK,
    PAGE_BLOCK,
    CLONE_BLOCK,
    COOLDOWN_BLOCK
}

enum class EnforcementEventType {
    APP_FOREGROUND_ENTER,
    APP_FOREGROUND_EXIT,
    PAGE_CHANGED,
    PAGE_CONTENT_CHANGED,
    SCREEN_ON,
    SCREEN_OFF,
    USER_PRESENT,
    RULE_CHANGED,
    TICK,
    SLEEP_WINDOW_STARTED,
    SLEEP_WINDOW_ENDED,
    APP_INSTALLED,
    APP_UNINSTALLED,
    CLONE_RESOLVED,
    CLONE_UNRESOLVED,
    OFFLINE_GAP_DETECTED,
    OVERLAY_SHOWN,
    OVERLAY_REMOVED
}

enum class EnforcementAction {
    ALLOW,
    SHOW_APP_CHALLENGE,
    SHOW_APP_LIMIT_BLOCK,
    SHOW_PHONE_LIMIT_BLOCK,
    SHOW_SLEEP_LOCK,
    SHOW_PAGE_BLOCK,
    SHOW_CLONE_BLOCK,
    SHOW_COOLDOWN_BLOCK,
    GO_HOME,
    NO_OP,
    FAIL_CLOSED_HOME,
    FAIL_CLOSED_GLOBAL
}

enum class ReasonCode {
    SAFE_ZONE_ALLOW,
    SELF_OR_SYSTEM_ALLOW,
    EMERGENCY_ALLOW,
    SYSTEM_HEALTH_FAIL_CLOSED,
    CLONE_POLICY_BLOCK,
    SLEEP_LOCK_BLOCK,
    PHONE_TOTAL_LIMIT_BLOCK,
    PHONE_SESSION_LIMIT_BLOCK,
    APP_DAILY_LIMIT_BLOCK,
    APP_SESSION_LIMIT_BLOCK,
    APP_CONTINUOUS_USE_BLOCK,
    APP_OPEN_COUNT_BLOCK,
    PAGE_KEYWORD_BLOCK,
    APP_COOLDOWN_BLOCK,
    APP_CHALLENGE_REQUIRED,
    ACTIVE_PASS_ALLOW,
    POLICY_DISABLED_ALLOW,
    MISSING_APP_POLICY,
    PAGE_CONTEXT_MISSING
}

enum class PageAction {
    BLOCK,
    CHALLENGE,
    RECORD_ONLY
}

enum class SystemHealthIssue {
    ACCESSIBILITY_DISCONNECTED,
    OVERLAY_PERMISSION_MISSING,
    NOTIFICATION_LISTENER_DISCONNECTED,
    BATTERY_OPTIMIZATION_ACTIVE,
    FOREGROUND_SERVICE_STOPPED
}

enum class SafeZoneCategory {
    SELF_APP,
    LAUNCHER,
    SYSTEM_UI,
    PHONE_OR_EMERGENCY_DIALER,
    INPUT_METHOD,
    SYSTEM_SETTINGS,
    PERMISSION_SETTINGS
}

class InvalidEnforcementContextException(message: String) : RuntimeException(message)

data class AppIdentity(
    val rawPackageName: String,
    val canonicalPackageName: String,
    val displayName: String,
    val identityType: IdentityType,
    val cloneGroupId: String,
    val containerPackageName: String,
    val userHandleKey: String,
    val isSystem: Boolean,
    val isLauncher: Boolean,
    val isEmergencyAllowed: Boolean
) {
    init {
        requireText(rawPackageName, "rawPackageName")
        requireText(canonicalPackageName, "canonicalPackageName")
        if (identityType == IdentityType.CLONE_CONTAINER && containerPackageName.isBlank()) {
            throw InvalidEnforcementContextException("containerPackageName is required for CLONE_CONTAINER")
        }
    }

    val identityKey: String
        get() = when {
            userHandleKey.isNotBlank() -> "$canonicalPackageName@$userHandleKey"
            cloneGroupId.isNotBlank() -> "$canonicalPackageName#$cloneGroupId"
            else -> canonicalPackageName
        }

    val isCloneLike: Boolean
        get() = identityType == IdentityType.SYSTEM_CLONE ||
                identityType == IdentityType.WORK_PROFILE ||
                identityType == IdentityType.CLONE_CONTAINER ||
                identityType == IdentityType.MANUAL_CLONE ||
                identityType == IdentityType.UNKNOWN_IDENTITY
}

data class SystemHealthState(
    val accessibilityConnected: Boolean,
    val overlayPermissionGranted: Boolean,
    val notificationListenerConnected: Boolean,
    val batteryOptimizationsIgnored: Boolean,
    val foregroundServiceRunning: Boolean
) {
    val fatalIssues: Set<SystemHealthIssue>
        get() = buildSet {
            if (!accessibilityConnected) add(SystemHealthIssue.ACCESSIBILITY_DISCONNECTED)
            if (!overlayPermissionGranted) add(SystemHealthIssue.OVERLAY_PERMISSION_MISSING)
            if (!foregroundServiceRunning) add(SystemHealthIssue.FOREGROUND_SERVICE_STOPPED)
        }

    val warningIssues: Set<SystemHealthIssue>
        get() = buildSet {
            if (!notificationListenerConnected) add(SystemHealthIssue.NOTIFICATION_LISTENER_DISCONNECTED)
            if (!batteryOptimizationsIgnored) add(SystemHealthIssue.BATTERY_OPTIMIZATION_ACTIVE)
        }

    val hasFatalIssue: Boolean
        get() = fatalIssues.isNotEmpty()
}

data class SafeZonePolicy(
    val selfPackageName: String,
    val launcherPackages: Set<String>,
    val systemUiPackages: Set<String>,
    val phonePackages: Set<String>,
    val inputMethodPackages: Set<String>,
    val systemSettingsPackages: Set<String>,
    val permissionSettingsPackages: Set<String>
) {
    init {
        requireText(selfPackageName, "selfPackageName")
        if (launcherPackages.isEmpty()) {
            throw InvalidEnforcementContextException("launcherPackages must not be empty")
        }
    }

    fun categoryFor(identity: AppIdentity): SafeZoneCategory? {
        val packageName = identity.rawPackageName
        return when {
            packageName == selfPackageName || identity.canonicalPackageName == selfPackageName ->
                SafeZoneCategory.SELF_APP
            identity.isLauncher || packageName in launcherPackages || identity.canonicalPackageName in launcherPackages ->
                SafeZoneCategory.LAUNCHER
            identity.isSystem && (packageName in systemUiPackages || identity.canonicalPackageName in systemUiPackages) ->
                SafeZoneCategory.SYSTEM_UI
            packageName in phonePackages || identity.canonicalPackageName in phonePackages || identity.isEmergencyAllowed ->
                SafeZoneCategory.PHONE_OR_EMERGENCY_DIALER
            packageName in inputMethodPackages || identity.canonicalPackageName in inputMethodPackages ->
                SafeZoneCategory.INPUT_METHOD
            packageName in systemSettingsPackages || identity.canonicalPackageName in systemSettingsPackages ->
                SafeZoneCategory.SYSTEM_SETTINGS
            packageName in permissionSettingsPackages || identity.canonicalPackageName in permissionSettingsPackages ->
                SafeZoneCategory.PERMISSION_SETTINGS
            else -> null
        }
    }

    fun isSafe(identity: AppIdentity): Boolean = categoryFor(identity) != null
}

data class PageSnapshot(
    val activityClassName: String,
    val visibleText: String
)

data class GlobalPolicy(
    val phoneDailyLimitMillis: Long,
    val phoneSessionLimitMillis: Long,
    val countWhitelistIdentities: Set<String>,
    val emergencyWhitelistIdentities: Set<String>
) {
    init {
        requireNonNegative(phoneDailyLimitMillis, "phoneDailyLimitMillis")
        requireNonNegative(phoneSessionLimitMillis, "phoneSessionLimitMillis")
    }
}

data class ClonePolicy(
    val enabled: Boolean,
    val blockUnknownClones: Boolean,
    val blockKnownCloneContainers: Boolean,
    val allowManualCloneRules: Boolean,
    val knownContainerPackages: Set<String>,
    val manualCloneIdentities: Set<String>
) {
    fun shouldBlock(identity: AppIdentity): Boolean {
        if (!enabled || !identity.isCloneLike) return false
        return when (identity.identityType) {
            IdentityType.MANUAL_CLONE ->
                !allowManualCloneRules && identity.identityKey !in manualCloneIdentities

            IdentityType.CLONE_CONTAINER -> {
                val knownContainer = identity.containerPackageName in knownContainerPackages
                (knownContainer && blockKnownCloneContainers) || (!knownContainer && blockUnknownClones)
            }

            IdentityType.SYSTEM_CLONE,
            IdentityType.WORK_PROFILE,
            IdentityType.UNKNOWN_IDENTITY -> blockUnknownClones

            IdentityType.NORMAL -> false
        }
    }
}

data class SleepPolicy(
    val enabled: Boolean
)

data class AppPolicy(
    val identityKey: String,
    val enabled: Boolean,
    val challengeEnabled: Boolean,
    val dailyLimitMillis: Long,
    val sessionLimitMillis: Long,
    val continuousUseLimitMillis: Long,
    val restRequiredMillis: Long,
    val dailyOpenLimit: Int,
    val passthroughMillis: Long,
    val cooldownAfterUseMillis: Long,
    val cooldownAfterQuitMillis: Long,
    val countTowardsPhoneUsage: Boolean
) {
    init {
        requireText(identityKey, "identityKey")
        requireNonNegative(dailyLimitMillis, "dailyLimitMillis")
        requireNonNegative(sessionLimitMillis, "sessionLimitMillis")
        requireNonNegative(continuousUseLimitMillis, "continuousUseLimitMillis")
        requireNonNegative(restRequiredMillis, "restRequiredMillis")
        requireNonNegative(dailyOpenLimit, "dailyOpenLimit")
        requireNonNegative(passthroughMillis, "passthroughMillis")
        requireNonNegative(cooldownAfterUseMillis, "cooldownAfterUseMillis")
        requireNonNegative(cooldownAfterQuitMillis, "cooldownAfterQuitMillis")
        if (enabled && !hasAnyRule()) {
            throw InvalidEnforcementContextException("enabled AppPolicy must define at least one rule: $identityKey")
        }
    }

    fun hasAnyRule(): Boolean =
        challengeEnabled ||
                dailyLimitMillis > 0L ||
                sessionLimitMillis > 0L ||
                continuousUseLimitMillis > 0L ||
                dailyOpenLimit > 0 ||
                cooldownAfterUseMillis > 0L ||
                cooldownAfterQuitMillis > 0L
}

data class PagePolicy(
    val identityKey: String,
    val enabled: Boolean,
    val activityClassNames: Set<String>,
    val keywordRules: Set<String>,
    val action: PageAction
) {
    init {
        requireText(identityKey, "identityKey")
        if (enabled && activityClassNames.isEmpty() && keywordRules.isEmpty()) {
            throw InvalidEnforcementContextException("enabled PagePolicy must define class names or keywords: $identityKey")
        }
    }

    fun matches(identityKey: String, page: PageSnapshot?): Boolean {
        if (!enabled || this.identityKey != identityKey) return false
        if (page == null) {
            throw InvalidEnforcementContextException("page context is required for enabled PagePolicy")
        }
        val classMatched = activityClassNames.isNotEmpty() && page.activityClassName in activityClassNames
        val keywordMatched = keywordRules.any { keyword ->
            keyword.isNotBlank() && page.visibleText.contains(keyword)
        }
        return classMatched || keywordMatched
    }
}

data class RuleSnapshot(
    val globalPolicy: GlobalPolicy,
    val clonePolicy: ClonePolicy,
    val sleepPolicy: SleepPolicy,
    val appPoliciesByIdentity: Map<String, AppPolicy>,
    val pagePoliciesByIdentity: Map<String, PagePolicy>
) {
    init {
        appPoliciesByIdentity.forEach { (identityKey, policy) ->
            if (identityKey != policy.identityKey) {
                throw InvalidEnforcementContextException(
                    "AppPolicy map key does not match policy identityKey: $identityKey"
                )
            }
        }
        pagePoliciesByIdentity.forEach { (identityKey, policy) ->
            if (identityKey != policy.identityKey) {
                throw InvalidEnforcementContextException(
                    "PagePolicy map key does not match policy identityKey: $identityKey"
                )
            }
            val appPolicy = appPoliciesByIdentity[identityKey]
                ?: throw InvalidEnforcementContextException(
                    "PagePolicy requires AppPolicy for identity: $identityKey"
                )
            if (!appPolicy.enabled) {
                throw InvalidEnforcementContextException(
                    "PagePolicy requires enabled AppPolicy for identity: $identityKey"
                )
            }
        }
    }

    fun appPolicyFor(identityKey: String): AppPolicy? = appPoliciesByIdentity[identityKey]

    fun pagePolicyFor(identityKey: String): PagePolicy? = pagePoliciesByIdentity[identityKey]

    fun requireAppPolicyFor(identityKey: String): AppPolicy =
        appPolicyFor(identityKey)
            ?: throw InvalidEnforcementContextException("missing AppPolicy for identity: $identityKey")
}

data class UsageSnapshot(
    val appDailyUsedMillis: Long,
    val appSessionUsedMillis: Long,
    val appContinuousUsedMillis: Long,
    val appDailyOpenCount: Int,
    val phoneDailyUsedMillis: Long,
    val phoneSessionUsedMillis: Long,
    val sleepLockActive: Boolean
) {
    init {
        requireNonNegative(appDailyUsedMillis, "appDailyUsedMillis")
        requireNonNegative(appSessionUsedMillis, "appSessionUsedMillis")
        requireNonNegative(appContinuousUsedMillis, "appContinuousUsedMillis")
        requireNonNegative(appDailyOpenCount, "appDailyOpenCount")
        requireNonNegative(phoneDailyUsedMillis, "phoneDailyUsedMillis")
        requireNonNegative(phoneSessionUsedMillis, "phoneSessionUsedMillis")
    }
}

data class ActivePass(
    val identityKey: String,
    val untilMillis: Long
) {
    init {
        requireText(identityKey, "identityKey")
        requireNonNegative(untilMillis, "untilMillis")
    }

    fun matches(identity: AppIdentity, nowMillis: Long): Boolean =
        identity.identityKey == identityKey && nowMillis < untilMillis
}

data class ActiveCooldown(
    val identityKey: String,
    val untilMillis: Long
) {
    init {
        requireText(identityKey, "identityKey")
        requireNonNegative(untilMillis, "untilMillis")
    }

    fun matches(identity: AppIdentity, nowMillis: Long): Boolean =
        identity.identityKey == identityKey && nowMillis < untilMillis
}

data class EnforcementContext(
    val nowMillis: Long,
    val eventType: EnforcementEventType,
    val foregroundApp: AppIdentity,
    val previousForegroundApp: AppIdentity?,
    val currentPage: PageSnapshot?,
    val pageContextMissingSinceMillis: Long?,
    val screenState: ScreenState,
    val activeOverlayType: OverlayType,
    val foregroundStartedAtMillis: Long,
    val sliceStartedAtMillis: Long,
    val ruleSnapshot: RuleSnapshot,
    val usageSnapshot: UsageSnapshot,
    val systemHealthState: SystemHealthState,
    val safeZonePolicy: SafeZonePolicy,
    val activePass: ActivePass?,
    val activeCooldown: ActiveCooldown?
) {
    init {
        requireNonNegative(nowMillis, "nowMillis")
        requireNonNegative(foregroundStartedAtMillis, "foregroundStartedAtMillis")
        requireNonNegative(sliceStartedAtMillis, "sliceStartedAtMillis")
        if (foregroundStartedAtMillis > nowMillis) {
            throw InvalidEnforcementContextException("foregroundStartedAtMillis is after nowMillis")
        }
        if (sliceStartedAtMillis > nowMillis) {
            throw InvalidEnforcementContextException("sliceStartedAtMillis is after nowMillis")
        }
    }
}

data class EnforcementDecision(
    val action: EnforcementAction,
    val priority: Int,
    val targetIdentity: AppIdentity,
    val reasonCode: ReasonCode,
    val reasonText: String,
    val durationMillis: Long,
    val overlayType: OverlayType,
    val eventsToRecord: List<String>
) {
    init {
        requireNonNegative(priority, "priority")
        requireNonNegative(durationMillis, "durationMillis")
    }
}

private fun requireText(value: String, field: String) {
    if (value.isBlank()) {
        throw InvalidEnforcementContextException("$field is required")
    }
}

private fun requireNonNegative(value: Long, field: String) {
    if (value < 0L) {
        throw InvalidEnforcementContextException("$field must be >= 0")
    }
}

private fun requireNonNegative(value: Int, field: String) {
    if (value < 0) {
        throw InvalidEnforcementContextException("$field must be >= 0")
    }
}
