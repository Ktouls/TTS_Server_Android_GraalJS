package com.github.jing332.script.simple

import android.content.Context
import com.github.jing332.script.engine.GraalScriptEngine
import com.github.jing332.script.runtime.Environment
import com.github.jing332.script.runtime.setEnvironment
import com.github.jing332.script.simple.ext.JsExtensions
import org.graalvm.polyglot.Value

/**
 * Compatible script runtime for migrating from Rhino to GraalVM
 */

class CompatScriptRuntime(val ttsrv: JsExtensions) :
    GraalScriptEngine(ttsrv.context) {

    private val environment = Environment(
        ttsrv.context.externalCacheDir?.absolutePath
            ?: throw IllegalArgumentException("context.externalCacheDir is null"),
        ttsrv.engineId
    )

    override fun init() {
        setEnvironment(environment)
        // Inject ttsrv into global scope
        put("ttsrv", ttsrv)
    }

    fun getGlobalScope(): Value {
        // GraalVM doesn't have globalScope like Rhino
        // Return the bindings instead
        throw UnsupportedOperationException("Use getBindings() or put()/get() instead")
    }
}