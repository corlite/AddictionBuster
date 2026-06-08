package com.addictionbuster.enforcement.storage

import android.util.AtomicFile
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

internal class AtomicJsonFile(file: File) {
    private val atomicFile = AtomicFile(file)

    @Synchronized
    fun readObjectOrNull(): JSONObject? {
        if (!atomicFile.baseFile.exists()) {
            return null
        }
        val bytes = atomicFile.openRead().use { it.readBytes() }
        val text = bytes.toString(StandardCharsets.UTF_8)
        if (text.isBlank()) {
            throw InvalidEnforcementContextException("atomic json file is blank: ${atomicFile.baseFile}")
        }
        return JSONObject(text)
    }

    @Synchronized
    fun writeObject(jsonObject: JSONObject) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(jsonObject.toString().toByteArray(StandardCharsets.UTF_8))
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(stream)
            throw throwable
        }
    }
}
