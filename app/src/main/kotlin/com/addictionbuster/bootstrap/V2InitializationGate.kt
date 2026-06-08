package com.addictionbuster.bootstrap

import android.content.Context
import com.addictionbuster.enforcement.storage.LocalSetupStateRepository

object V2InitializationGate {
    @JvmStatic
    fun requiresSetup(context: Context): Boolean =
        !LocalSetupStateRepository(context.applicationContext).isSetupCompleted()
}
