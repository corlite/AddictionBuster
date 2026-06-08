package com.addictionbuster

import android.content.Context
import com.addictionbuster.enforcement.AppPolicy
import com.addictionbuster.enforcement.ClonePolicy
import com.addictionbuster.enforcement.GlobalPolicy
import com.addictionbuster.enforcement.RuleSnapshot
import com.addictionbuster.enforcement.SleepPolicy
import com.addictionbuster.enforcement.identity.CloneContainerCatalog
import com.addictionbuster.enforcement.storage.LocalRuleRepository

object V2RuleBridge {
    @JvmStatic
    fun saveAppRule(context: Context, packageName: String, rule: AppRule) {
        val repository = LocalRuleRepository(context.applicationContext)
        val current = if (repository.hasRules()) {
            repository.load()
        } else {
            emptySnapshot()
        }
        val identityKey = packageName
        val updatedPolicy = AppPolicy(
            identityKey = identityKey,
            enabled = true,
            challengeEnabled = true,
            dailyLimitMillis = minutesToMillis(rule.dailyQuotaMinutes),
            sessionLimitMillis = minutesToMillis(rule.sessionLimitMinutes),
            continuousUseLimitMillis = 0L,
            restRequiredMillis = 0L,
            dailyOpenLimit = 0,
            passthroughMillis = minutesToMillis(rule.sessionLimitMinutes),
            cooldownAfterUseMillis = 0L,
            cooldownAfterQuitMillis = 0L,
            countTowardsPhoneUsage = true
        )
        repository.save(
            current.copy(
                appPoliciesByIdentity = current.appPoliciesByIdentity + (identityKey to updatedPolicy)
            )
        )
        DiagnosticLogger.log(context, "rule", "saved v2 app policy package=$packageName")
    }

    @JvmStatic
    fun clearAppRule(context: Context, packageName: String) {
        val repository = LocalRuleRepository(context.applicationContext)
        if (!repository.hasRules()) return
        val current = repository.load()
        repository.save(
            current.copy(
                appPoliciesByIdentity = current.appPoliciesByIdentity - packageName,
                pagePoliciesByIdentity = current.pagePoliciesByIdentity - packageName
            )
        )
        DiagnosticLogger.log(context, "rule", "cleared v2 app policy package=$packageName")
    }

    private fun emptySnapshot(): RuleSnapshot =
        RuleSnapshot(
            globalPolicy = GlobalPolicy(
                phoneDailyLimitMillis = 0L,
                phoneSessionLimitMillis = 0L,
                countWhitelistIdentities = emptySet(),
                emergencyWhitelistIdentities = emptySet()
            ),
            clonePolicy = ClonePolicy(
                enabled = true,
                blockUnknownClones = true,
                blockKnownCloneContainers = true,
                allowManualCloneRules = false,
                knownContainerPackages = CloneContainerCatalog.knownContainerPackages,
                manualCloneIdentities = emptySet()
            ),
            sleepPolicy = SleepPolicy(enabled = false, windows = emptyList()),
            appPoliciesByIdentity = emptyMap(),
            pagePoliciesByIdentity = emptyMap()
        )

    private fun minutesToMillis(minutes: Int): Long =
        minutes.coerceAtLeast(0).toLong() * 60_000L
}
