package com.addictionbuster.enforcement.storage

import android.content.Context
import com.addictionbuster.enforcement.ActivePass
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import org.json.JSONObject
import java.io.File

class LocalPassRepository(context: Context) {
    private val passFile = AtomicJsonFile(File(storageDir(context), "active_pass.json"))

    @Synchronized
    fun load(): ActivePass? =
        passFile.readObjectOrNull()?.toActivePass()

    @Synchronized
    fun save(activePass: ActivePass) {
        passFile.writeObject(activePass.toJson())
    }

    @Synchronized
    fun clear() {
        passFile.writeObject(JSONObject().put("cleared", true))
    }

    @Synchronized
    fun requireActivePass(): ActivePass =
        load() ?: throw InvalidEnforcementContextException("missing active pass")
}

private fun ActivePass.toJson(): JSONObject =
    JSONObject()
        .put("identityKey", identityKey)
        .put("untilMillis", untilMillis)

private fun JSONObject.toActivePass(): ActivePass? {
    if (optBoolean("cleared", false)) return null
    return ActivePass(
        identityKey = getString("identityKey"),
        untilMillis = getLong("untilMillis")
    )
}
