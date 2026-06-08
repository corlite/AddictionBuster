package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.AppPolicy
import com.addictionbuster.enforcement.ClonePolicy
import com.addictionbuster.enforcement.GlobalPolicy
import com.addictionbuster.enforcement.IdentityType
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.PageAction
import com.addictionbuster.enforcement.PagePolicy
import com.addictionbuster.enforcement.RuleSnapshot
import com.addictionbuster.enforcement.SleepPolicy
import com.addictionbuster.enforcement.SleepWindow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalRuleRepository(context: Context) {
    private val ruleFile = AtomicJsonFile(File(storageDir(context), "rules.json"))

    @Synchronized
    fun load(): RuleSnapshot =
        ruleFile.readObjectOrNull()?.toRuleSnapshot()
            ?: throw InvalidEnforcementContextException("missing v2 rule snapshot")

    @Synchronized
    fun save(ruleSnapshot: RuleSnapshot) {
        ruleFile.writeObject(ruleSnapshot.toJson())
    }

    @Synchronized
    fun hasRules(): Boolean = ruleFile.readObjectOrNull() != null
}

private fun RuleSnapshot.toJson(): JSONObject =
    JSONObject()
        .put("globalPolicy", globalPolicy.toJson())
        .put("clonePolicy", clonePolicy.toJson())
        .put("sleepPolicy", sleepPolicy.toJson())
        .put("appPolicies", appPoliciesByIdentity.values.map { it.toJson() }.toJsonObjectArray())
        .put("pagePolicies", pagePoliciesByIdentity.values.map { it.toJson() }.toJsonObjectArray())

private fun JSONObject.toRuleSnapshot(): RuleSnapshot {
    val appPolicies = getJSONArray("appPolicies").objects()
        .map { it.toAppPolicy() }
        .associateBy { it.identityKey }
    val pagePolicies = getJSONArray("pagePolicies").objects()
        .map { it.toPagePolicy() }
        .associateBy { it.identityKey }
    return RuleSnapshot(
        globalPolicy = getJSONObject("globalPolicy").toGlobalPolicy(),
        clonePolicy = getJSONObject("clonePolicy").toClonePolicy(),
        sleepPolicy = getJSONObject("sleepPolicy").toSleepPolicy(),
        appPoliciesByIdentity = appPolicies,
        pagePoliciesByIdentity = pagePolicies
    )
}

private fun GlobalPolicy.toJson(): JSONObject =
    JSONObject()
        .put("phoneDailyLimitMillis", phoneDailyLimitMillis)
        .put("phoneSessionLimitMillis", phoneSessionLimitMillis)
        .put("countWhitelistIdentities", countWhitelistIdentities.toJsonStringArray())
        .put("emergencyWhitelistIdentities", emergencyWhitelistIdentities.toJsonStringArray())

private fun JSONObject.toGlobalPolicy(): GlobalPolicy =
    GlobalPolicy(
        phoneDailyLimitMillis = getLong("phoneDailyLimitMillis"),
        phoneSessionLimitMillis = getLong("phoneSessionLimitMillis"),
        countWhitelistIdentities = getJSONArray("countWhitelistIdentities").strings(),
        emergencyWhitelistIdentities = getJSONArray("emergencyWhitelistIdentities").strings()
    )

private fun ClonePolicy.toJson(): JSONObject =
    JSONObject()
        .put("enabled", enabled)
        .put("blockUnknownClones", blockUnknownClones)
        .put("blockKnownCloneContainers", blockKnownCloneContainers)
        .put("allowManualCloneRules", allowManualCloneRules)
        .put("knownContainerPackages", knownContainerPackages.toJsonStringArray())
        .put("manualCloneIdentities", manualCloneIdentities.toJsonStringArray())

private fun JSONObject.toClonePolicy(): ClonePolicy =
    ClonePolicy(
        enabled = getBoolean("enabled"),
        blockUnknownClones = getBoolean("blockUnknownClones"),
        blockKnownCloneContainers = getBoolean("blockKnownCloneContainers"),
        allowManualCloneRules = getBoolean("allowManualCloneRules"),
        knownContainerPackages = getJSONArray("knownContainerPackages").strings(),
        manualCloneIdentities = getJSONArray("manualCloneIdentities").strings()
    )

private fun SleepPolicy.toJson(): JSONObject =
    JSONObject()
        .put("enabled", enabled)
        .put("windows", windows.map { it.toJson() }.toJsonObjectArray())

private fun JSONObject.toSleepPolicy(): SleepPolicy =
    SleepPolicy(
        enabled = getBoolean("enabled"),
        windows = getJSONArray("windows").objects().map { it.toSleepWindow() }
    )

private fun SleepWindow.toJson(): JSONObject =
    JSONObject()
        .put("startMinuteOfDay", startMinuteOfDay)
        .put("endMinuteOfDay", endMinuteOfDay)
        .put("activeDays", activeDays.map { it.toString() }.toJsonStringArray())

private fun JSONObject.toSleepWindow(): SleepWindow =
    SleepWindow(
        startMinuteOfDay = getInt("startMinuteOfDay"),
        endMinuteOfDay = getInt("endMinuteOfDay"),
        activeDays = getJSONArray("activeDays").strings().map { it.toInt() }.toSet()
    )

private fun AppPolicy.toJson(): JSONObject =
    JSONObject()
        .put("identityKey", identityKey)
        .put("enabled", enabled)
        .put("challengeEnabled", challengeEnabled)
        .put("dailyLimitMillis", dailyLimitMillis)
        .put("sessionLimitMillis", sessionLimitMillis)
        .put("continuousUseLimitMillis", continuousUseLimitMillis)
        .put("restRequiredMillis", restRequiredMillis)
        .put("dailyOpenLimit", dailyOpenLimit)
        .put("passthroughMillis", passthroughMillis)
        .put("cooldownAfterUseMillis", cooldownAfterUseMillis)
        .put("cooldownAfterQuitMillis", cooldownAfterQuitMillis)
        .put("countTowardsPhoneUsage", countTowardsPhoneUsage)

private fun JSONObject.toAppPolicy(): AppPolicy =
    AppPolicy(
        identityKey = getString("identityKey"),
        enabled = getBoolean("enabled"),
        challengeEnabled = getBoolean("challengeEnabled"),
        dailyLimitMillis = getLong("dailyLimitMillis"),
        sessionLimitMillis = getLong("sessionLimitMillis"),
        continuousUseLimitMillis = getLong("continuousUseLimitMillis"),
        restRequiredMillis = getLong("restRequiredMillis"),
        dailyOpenLimit = getInt("dailyOpenLimit"),
        passthroughMillis = getLong("passthroughMillis"),
        cooldownAfterUseMillis = getLong("cooldownAfterUseMillis"),
        cooldownAfterQuitMillis = getLong("cooldownAfterQuitMillis"),
        countTowardsPhoneUsage = getBoolean("countTowardsPhoneUsage")
    )

private fun PagePolicy.toJson(): JSONObject =
    JSONObject()
        .put("identityKey", identityKey)
        .put("enabled", enabled)
        .put("activityClassNames", activityClassNames.toJsonStringArray())
        .put("keywordRules", keywordRules.toJsonStringArray())
        .put("action", action.name)

private fun JSONObject.toPagePolicy(): PagePolicy =
    PagePolicy(
        identityKey = getString("identityKey"),
        enabled = getBoolean("enabled"),
        activityClassNames = getJSONArray("activityClassNames").strings(),
        keywordRules = getJSONArray("keywordRules").strings(),
        action = PageAction.valueOf(getString("action"))
    )

private fun Collection<String>.toJsonStringArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

private fun Collection<JSONObject>.toJsonObjectArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

private fun JSONArray.strings(): Set<String> =
    buildSet {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }

private fun JSONArray.objects(): List<JSONObject> =
    buildList {
        for (index in 0 until length()) {
            add(getJSONObject(index))
        }
    }
