package com.addictionbuster.bootstrap

import android.content.Context
import com.addictionbuster.enforcement.storage.LocalRuleRepository

object V2InitializationGate {
    @JvmStatic
    fun requiresSetup(context: Context): Boolean =
        !LocalRuleRepository(context.applicationContext).hasRules()
}
