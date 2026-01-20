package com.github.jing332.tts_server_android.model.rhino.direct_link_upload

import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.simple.SimpleScriptEngine
import com.github.jing332.script.source.StringScriptSource
import com.github.jing332.tts_server_android.conf.DirectUploadConfig
import org.graalvm.polyglot.Value

class DirectUploadEngine(
    context: android.content.Context,
    var code: String = DirectUploadConfig.code.value,
) {
    val engine = SimpleScriptEngine(context, "direct_link_upload")
    val console: Console
        get() = engine.runtime.console


    companion object {
        private const val TAG = "DirectUploadEngine"
        const val OBJ_DIRECT_UPLOAD = "DirectUploadJS"
    }


    private val jsObject: Map<String, Any?>
        get() = engine.get(OBJ_DIRECT_UPLOAD) as Map<String, Any?>

    /**
     * 获取所有方法
     */
    fun obtainFunctionList(): List<DirectUploadFunction> {
        engine.execute(StringScriptSource(code))
        return jsObject.mapNotNull { (key, value) ->
            if (value is Value && value.canExecute()) {
                DirectUploadFunction(function = value, name = key)
            } else {
                null
            }
        }
    }

}