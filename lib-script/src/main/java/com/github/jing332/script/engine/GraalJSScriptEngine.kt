package com.github.jing332.script.engine

import com.github.jing332.script.JavaScriptEngine
import com.github.jing332.script.runtime.GraalJSScriptRuntime
import com.github.jing332.script.source.ReaderScriptSource
import com.github.jing332.script.source.ScriptSource
import com.github.jing332.script.source.StringScriptSource
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

open class GraalJSScriptEngine(val runtime: GraalJSScriptRuntime) :
    JavaScriptEngine() {

    private val context by lazy { runtime.createContext() }

    init {
        runtime.init()
    }

    @Deprecated("auto init")
    override fun init() {
    }

    override fun destroy() {
        context.close()
    }

    override fun execute(source: ScriptSource): Any? {
        val graalSource = when (source) {
            is ReaderScriptSource -> {
                Source.newBuilder("js", source.reader)
                    .name(source.sourceName)
                    .cached(false)
                    .build()
            }

            is StringScriptSource -> {
                Source.newBuilder("js", source.script, source.sourceName)
                    .build()
            }

            else -> throw IllegalArgumentException("Unsupported source type: ${source::class.java.name}")
        }

        return context.eval(graalSource).asHostObject<Any>()
    }

    override fun put(key: String, value: Any?) {
        val bindings = context.getBindings("js")
        bindings.putMember(key, context.asValue(value))
    }

    override fun get(key: String): Any? {
        val bindings = context.getBindings("js")
        val value = bindings.getMember(key)
        return if (value.isNull) null else value.asHostObject<Any>()
    }

    fun invokeMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val jsValue = context.asValue(obj)
        val receiver = if (jsValue.isHostObject) jsValue else jsValue

        val method = receiver.getMember(name)
        if (!method.canExecute()) {
            throw NoSuchMethodException(name)
        }

        val graalArgs = args.map { context.asValue(it) }.toTypedArray()
        val result = method.execute(*graalArgs)
        return if (result.isNull) null else result.asHostObject<Any>()
    }

    fun invokeFunction(name: String, vararg args: Any?): Any? {
        val bindings = context.getBindings("js")
        val func = bindings.getMember(name)
        if (!func.canExecute()) {
            throw NoSuchMethodException(name)
        }

        val graalArgs = args.map { context.asValue(it) }.toTypedArray()
        val result = func.execute(*graalArgs)
        return if (result.isNull) null else result.asHostObject<Any>()
    }

    fun getValue(key: String): Value? {
        val bindings = context.getBindings("js")
        return bindings.getMember(key)
    }

    fun asValue(obj: Any?): Value = context.asValue(obj)

}
