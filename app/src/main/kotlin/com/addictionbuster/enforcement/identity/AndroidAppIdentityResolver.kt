package com.addictionbuster.enforcement.identity

import android.content.Context
import android.content.pm.PackageManager
import com.addictionbuster.enforcement.AppIdentity
import com.addictionbuster.enforcement.IdentityType
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.SafeZonePolicy

class AndroidAppIdentityResolver(
    private val context: Context,
    private val safeZonePolicy: SafeZonePolicy,
    private val knownContainerPackages: Set<String> = CloneContainerCatalog.knownContainerPackages,
    private val manualCloneIdentityKeys: Set<String> = emptySet()
) {
    fun resolve(
        rawPackageName: String,
        displayName: String = "",
        userHandleKey: String = "",
        containerPackageName: String = ""
    ): AppIdentity {
        if (rawPackageName.isBlank()) {
            throw InvalidEnforcementContextException("rawPackageName is required")
        }
        val packageKnown = isInstalledOrAndroidSystem(rawPackageName)
        val identityType = resolveIdentityType(
            rawPackageName = rawPackageName,
            userHandleKey = userHandleKey,
            containerPackageName = containerPackageName,
            packageKnown = packageKnown
        )
        val canonicalPackageName = if (containerPackageName.isNotBlank()) {
            rawPackageName
        } else {
            rawPackageName
        }
        val provisionalIdentity = AppIdentity(
            rawPackageName = rawPackageName,
            canonicalPackageName = canonicalPackageName,
            displayName = displayName,
            identityType = identityType,
            cloneGroupId = if (identityType == IdentityType.MANUAL_CLONE) "manual" else "",
            containerPackageName = if (identityType == IdentityType.CLONE_CONTAINER) {
                rawPackageName
            } else {
                containerPackageName
            },
            userHandleKey = userHandleKey,
            isSystem = isSystemPackage(rawPackageName),
            isLauncher = rawPackageName in safeZonePolicy.launcherPackages,
            isEmergencyAllowed = rawPackageName in safeZonePolicy.phonePackages
        )
        val manualKey = provisionalIdentity.identityKey
        return if (manualKey in manualCloneIdentityKeys && identityType == IdentityType.NORMAL) {
            provisionalIdentity.copy(
                identityType = IdentityType.MANUAL_CLONE,
                cloneGroupId = "manual"
            )
        } else {
            provisionalIdentity
        }
    }

    private fun resolveIdentityType(
        rawPackageName: String,
        userHandleKey: String,
        containerPackageName: String,
        packageKnown: Boolean
    ): IdentityType {
        if (!packageKnown) return IdentityType.UNKNOWN_IDENTITY
        if (rawPackageName in knownContainerPackages) return IdentityType.CLONE_CONTAINER
        if (containerPackageName in knownContainerPackages) return IdentityType.CLONE_CONTAINER
        if (userHandleKey.isNotBlank() && userHandleKey != "owner") return IdentityType.WORK_PROFILE
        return IdentityType.NORMAL
    }

    private fun isInstalledOrAndroidSystem(packageName: String): Boolean {
        if (packageName == "android") return true
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        if (packageName == "android") return true
        return packageName in safeZonePolicy.systemUiPackages ||
                packageName in safeZonePolicy.systemSettingsPackages
    }
}
