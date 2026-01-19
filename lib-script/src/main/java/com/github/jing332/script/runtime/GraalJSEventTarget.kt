package com.github.jing332.script.runtime

import com.github.jing332.common.utils.firstCharUpperCase
import io.github.oshai.kotlinlogging.KotlinLogging
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class GraalJSEventTarget(private val context: Context) {
    companion object {
        val logger = KotlinLogging.logger("GraalJSEventTarget")
    }

    val functions = hashMapOf<String, Value>()

    private fun addEventListener(eventName: String, function: Value) {
        functions[eventName] = function
    }

    fun install(bindings: Value) {
        val onFunc = context.asValue { args: Array<Value> ->
            val eventName = args[0].asString()
            val function = args[1]
            if (function.canExecute()) {
                addEventListener(eventName, function)
            }
            null
        }
        bindings.putMember("on", onFunc)

        val addEventListenerFunc = context.asValue { args: Array<Value> ->
            val eventName = args[0].asString()
            val function = args[1]
            if (function.canExecute()) {
                addEventListener(eventName, function)
            }
            null
        }
        bindings.putMember("addEventListener", addEventListenerFunc)
    }

    fun emit(eventName: String, vararg args: Any?) {
        val function = functions[eventName]
            ?: run {
                val propName = "on${eventName.firstCharUpperCase()}"
                bindings?.getMember(propName)?.takeIf { it.canExecute() }
            }

        try {
            function?.execute(*args.map { context.asValue(it) }.toTypedArray())
        } catch (e: Exception) {
            logger.error(e) { "emit error" }
            runCatching {
                functions["error"]?.execute(e)
            }
        }
    }

    private val bindings = context.getBindings("js")
}
