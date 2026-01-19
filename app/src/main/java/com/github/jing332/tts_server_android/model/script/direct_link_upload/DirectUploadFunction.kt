package com.github.jing332.tts_server_android.model.script.direct_link_upload

import com.github.jing332.script.simple.SimpleScriptEngine
import org.graalvm.polyglot.Value

data class DirectUploadFunction(
    val function: Value?,
    val name: String,
    val scope: Value?,
    val thisObj: Value,
) {
    fun invoke(config: String, engine: SimpleScriptEngine): String? {
        return engine.invokeMethod(thisObj, name, config)?.toString() ?: ""
    }

}