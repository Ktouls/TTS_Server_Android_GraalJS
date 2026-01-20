package com.github.jing332.script.engine

import android.content.Context
import com.github.jing332.script.JavaScriptEngine
import com.github.jing332.script.source.ScriptSource
import org.graalvm.polyglot.Context as PolyglotContext
import org.graalvm.polyglot.Value
import java.util.concurrent.ConcurrentHashMap

/**
 * GraalVM JavaScript Engine - Drop-in replacement for RhinoScriptEngine
 *
 * Maintains full API compatibility while using GraalVM 20.3.13 internally.
 * All Rhino-specific features are emulated via GraalJsRuntime.
 */
open class GraalScriptEngine(
    private val androidContext: Context? = null,
    private val classLoader: ClassLoader? = null
) : JavaScriptEngine() {
    private var polyglotContext: PolyglotContext? = null
    private val bindings = ConcurrentHashMap<String, Any?>()
    private var globalScope: Value? = null
    
    init {
        // Initialize GraalVM context lazily
    }
    
    private fun ensureContext(): PolyglotContext {
        return polyglotContext ?: GraalJsRuntime.createContext(androidContext, classLoader).also {
            polyglotContext = it
            globalScope = it.getBindings("js")
        }
    }
    
    /**
     * Executes JavaScript source code.
     *
     * @param source Script source (string, reader, etc.)
     * @return Result converted to Java type via ScriptValueUtils
     */
    override fun execute(source: ScriptSource): Any? {
        val context = ensureContext()
        val script = when (source) {
            is com.github.jing332.script.source.StringScriptSource -> source.script
            is com.github.jing332.script.source.ReaderScriptSource -> source.reader.readText()
            else -> throw IllegalArgumentException("Unsupported source type: ${source::class.java.name}")
        }
        
        return GraalJsRuntime.executeScript(context, script, source.sourceName)
    }
    
    /**
     * Binds a Java object to the global scope (Rhino-style).
     */
    override fun put(key: String, value: Any?) {
        bindings[key] = value
        globalScope?.putMember(key, value)
    }
    
    /**
     * Retrieves a value from the global scope.
     */
    override fun get(key: String): Any? {
        return bindings[key] ?: globalScope?.getMember(key)?.let { ScriptValueUtils.toJavaType(it) }
    }
    
    /**
     * Invokes a JavaScript function by name.
     *
     * @param name Function name in global scope
     * @param args Arguments to pass
     * @return Function result converted to Java type
     * @throws NoSuchMethodException if function doesn't exist
     */
    fun invokeFunction(name: String, vararg args: Any?): Any? {
        val context = ensureContext()
        val bindings = context.getBindings("js")
        
        val function = bindings.getMember(name)
        if (!function.canExecute()) {
            throw NoSuchMethodException("JavaScript function '$name' not found or not executable")
        }
        
        val jsArgs = args.map { ScriptValueUtils.toJsValue(it) }.toTypedArray()
        return try {
            val result = function.execute(*jsArgs)
            ScriptValueUtils.toJavaType(result)
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke function '$name': ${e.message}", e)
        }
    }
    
    /**
     * Invokes a method on a JavaScript object.
     *
     * @param objectName Name of object in global scope
     * @param methodName Method name to call
     * @param args Arguments to pass
     * @return Method result converted to Java type
     */
    fun invokeMethod(objectName: String, methodName: String, vararg args: Any?): Any? {
        val context = ensureContext()
        val bindings = context.getBindings("js")
        
        val obj = bindings.getMember(objectName)
        if (obj.isNull || !obj.hasMembers()) {
            throw IllegalArgumentException("Object '$objectName' not found")
        }
        
        val method = obj.getMember(methodName)
        if (!method.canExecute()) {
            throw NoSuchMethodException("Method '$methodName' not found on object '$objectName'")
        }
        
        val jsArgs = args.map { ScriptValueUtils.toJsValue(it) }.toTypedArray()
        return try {
            val result = method.execute(obj, *jsArgs)
            ScriptValueUtils.toJavaType(result)
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke method '$methodName': ${e.message}", e)
        }
    }
    
    /**
     * Safely converts a JavaScript value to a specific Java type.
     * Use this when you know the expected return type (e.g., byte[] for audio).
     *
     * @param value JavaScript value (Value or already converted)
     * @param type Target Java class
     * @return Casted value of type T
     */
    fun <T> cast(value: Any?, type: Class<T>): T {
        return when (value) {
            is Value -> ScriptValueUtils.toJavaType(value) as? T ?: throw ClassCastException()
            else -> type.cast(value) ?: throw ClassCastException()
        }
    }
    
    /**
     * Releases GraalVM context resources.
     * Call when engine is no longer needed.
     */
    fun destroy() {
        polyglotContext?.close()
        polyglotContext = null
        globalScope = null
        bindings.clear()
    }
    
    /**
     * Convenience function to run code on UI thread from JavaScript.
     */
    fun runOnUiThread(block: () -> Unit) {
        GraalJsRuntime.runOnUiThread(block)
    }
}