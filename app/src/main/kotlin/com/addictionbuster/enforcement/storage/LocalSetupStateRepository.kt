package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import org.json.JSONObject
import java.io.File

class LocalSetupStateRepository(context: Context) {
    private val setupFile = AtomicJsonFile(File(storageDir(context), "setup_state.json"))

    @Synchronized
    fun isSetupCompleted(): Boolean =
        setupFile.readObjectOrNull()?.optBoolean("setupCompleted", false) == true

    @Synchronized
    fun markSetupCompleted(completedAtMillis: Long) {
        if (completedAtMillis < 0L) {
            throw InvalidEnforcementContextException("completedAtMillis must be >= 0")
        }
        setupFile.writeObject(
            JSONObject()
                .put("setupCompleted", true)
                .put("completedAtMillis", completedAtMillis)
        )
    }
}
