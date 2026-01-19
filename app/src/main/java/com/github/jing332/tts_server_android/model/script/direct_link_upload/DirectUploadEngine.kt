package com.github.jing332.tts_server_android.model.script.direct_link_upload

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


    private val jsObject: Value
        get() = engine.getValue(OBJ_DIRECT_UPLOAD) as? Value
            ?: throw IllegalStateException("$OBJ_DIRECT_UPLOAD not found")

    /**
     * 获取所有方法
     */
    fun obtainFunctionList(): List<DirectUploadFunction> {
        engine.execute(StringScriptSource(code))
        val result = mutableListOf<DirectUploadFunction>()

        if (jsObject.hasMembers()) {
            for (key in jsObject.memberKeys) {
                val member = jsObject.getMember(key)
                if (member.canExecute()) {
                    result.add(
                        DirectUploadFunction(
                            function = null,
                            name = key,
                            scope = null,
                            thisObj = jsObject
                        )
                    )
                }
            }
        }

        return result
    }

}