package com.addictionbuster.enforcement.storage

import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementEventType
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.UsageCommit

class UsageCommitWriter(
    private val appUsageRepository: LocalAppUsageRepository
) {
    @Synchronized
    fun commit(previousContext: EnforcementContext, usageCommit: UsageCommit): AppUsageState {
        val identity = previousContext.foregroundApp.identityKey
        if (identity.isBlank()) {
            throw InvalidEnforcementContextException("foreground identity is required for usage commit")
        }
        val withOpenCount = if (previousContext.eventType == EnforcementEventType.APP_FOREGROUND_ENTER) {
            appUsageRepository.incrementOpen(identity, previousContext.foregroundStartedAtMillis)
        } else {
            appUsageRepository.load(identity)
        }
        if (usageCommit.appUsageMillis == 0L) {
            return withOpenCount
        }
        return appUsageRepository.addUsage(
            identityKey = identity,
            appUsageMillis = usageCommit.appUsageMillis
        )
    }

    @Synchronized
    fun markOfflineGapPending(identityKey: String, durationMillis: Long): AppUsageState =
        appUsageRepository.markOfflineGapPending(identityKey, durationMillis)
}
