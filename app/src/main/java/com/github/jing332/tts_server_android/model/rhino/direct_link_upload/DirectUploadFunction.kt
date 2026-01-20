package com.github.jing332.tts_server_android.model.rhino.direct_link_upload

import com.github.jing332.script.engine.ScriptValueUtils
import org.graalvm.polyglot.Value

data class DirectUploadFunction(
    val function: Value,
    val name: String,
) {
    fun invoke(config: String): String? {
        if (!function.canExecute()) {
            throw IllegalArgumentException("$name is not a function")
        }
        val result = function.execute(config)
        return ScriptValueUtils.toJavaType(result)?.toString()
    }

}