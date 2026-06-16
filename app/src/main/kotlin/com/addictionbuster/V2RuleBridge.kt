package com.addictionbuster

import android.content.Context
import com.addictionbuster.enforcement.AppPolicy
import com.addictionbuster.enforcement.ClonePolicy
import com.addictionbuster.enforcement.GlobalPolicy
import com.addictionbuster.enforcement.RuleSnapshot
import com.addictionbuster.enforcement.SleepPolicy
import com.addictionbuster.enforcement.identity.CloneContainerCatalog
import com.addictionbuster.enforcement.storage.LocalPassRepository
import com.addictionbuster.enforcement.storage.LocalPhoneUsageRepository
import com.addictionbuster.enforcement.storage.LocalRuleRepository

object V2RuleBridge {
    @JvmStatic
    fun saveAppRule(context: Context, packageName: String, rule: AppRule) {
        val repository = LocalRuleRepository(context.applicationContext)
        val current = loadCurrentOrFresh(context, repository, "save app rule package=$packageName")
        val identityKey = packageName
        val updatedPolicy = AppPolicy(
            identityKey = identityKey,
            enabled = true,
            challengeEnabled = true,
            dailyLimitMillis = minutesToMillis(rule.dailyQuotaMinutes),
            sessionLimitMillis = 0L,
            continuousUseLimitMillis = 0L,
            restRequiredMillis = 0L,
            dailyOpenLimit = 0,
            passthroughMillis = minutesToMillis(rule.sessionLimitMinutes),
            challengeWaitMillis = secondsToMillis(rule.waitSeconds),
            challengeRequiredTaps = rule.requiredTaps.coerceAtLeast(0),
            challengeHiddenCount = rule.hiddenCount.coerceAtLeast(0),
            challengeHiddenMillis = secondsToMillis(rule.hiddenSeconds),
            challengeConfirmText = rule.confirmText,
            cooldownAfterUseMillis = 0L,
            cooldownAfterQuitMillis = 0L,
            countTowardsPhoneUsage = true
        )
        repository.save(
            current.copy(
                appPoliciesByIdentity = current.appPoliciesByIdentity + (identityKey to updatedPolicy)
            )
        )
        LocalPassRepository(context.applicationContext).clear()
        DiagnosticLogger.log(context, "rule", "saved v2 app policy package=$packageName clearedActivePass=true")
    }

    @JvmStatic
    fun clearAppRule(context: Context, packageName: String) {
        val repository = LocalRuleRepository(context.applicationContext)
        if (!repository.hasRules()) return
        val current = loadCurrentOrFresh(context, repository, "clear app rule package=$packageName")
        repository.save(
            current.copy(
                appPoliciesByIdentity = current.appPoliciesByIdentity - packageName,
                pagePoliciesByIdentity = current.pagePoliciesByIdentity - packageName
            )
        )
        LocalPassRepository(context.applicationContext).clear()
        DiagnosticLogger.log(context, "rule", "cleared v2 app policy package=$packageName clearedActivePass=true")
    }

    @JvmStatic
    fun savePhoneLimits(context: Context, dailyLimitMinutes: Int, sessionLimitMinutes: Int) {
        val repository = LocalRuleRepository(context.applicationContext)
        val current = loadCurrentOrFresh(context, repository, "save phone limits")
        repository.save(
            current.copy(
                globalPolicy = current.globalPolicy.copy(
                    phoneDailyLimitMillis = minutesToMillis(dailyLimitMinutes),
                    phoneSessionLimitMillis = minutesToMillis(sessionLimitMinutes)
                )
            )
        )
        DiagnosticLogger.log(
            context,
            "rule",
            "saved v2 phone limits dailyMinutes=${dailyLimitMinutes.coerceAtLeast(0)} sessionMinutes=${sessionLimitMinutes.coerceAtLeast(0)}"
        )
    }

    @JvmStatic
    fun savePhoneWhitelist(context: Context, packageNames: Set<String>) {
        val repository = LocalRuleRepository(context.applicationContext)
        val current = loadCurrentOrFresh(context, repository, "save phone whitelist")
        repository.save(
            current.copy(
                globalPolicy = current.globalPolicy.copy(
                    countWhitelistIdentities = packageNames.toSet()
                )
            )
        )
        DiagnosticLogger.log(context, "rule", "saved v2 phone whitelist count=${packageNames.size}")
    }

    @JvmStatic
    fun getPhoneDailyUsedMinutes(context: Context): Long =
        LocalPhoneUsageRepository(context.applicationContext).load().dailyUsedMillis / 60_000L

    private fun loadCurrentOrFresh(
        context: Context,
        repository: LocalRuleRepository,
        reason: String
    ): RuleSnapshot {
        if (!repository.hasRules()) return emptySnapshot()
        return try {
            repository.load()
        } catch (throwable: Throwable) {
            DiagnosticLogger.log(
                context,
                "rule",
                "rebuild v2 rule snapshot reason=$reason error=${throwable.message}"
            )
            emptySnapshot()
        }
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

    private fun secondsToMillis(seconds: Int): Long =
        seconds.coerceAtLeast(0).toLong() * 1000L
}
