package com.github.jing332.script.engine

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.reflect.KClass

/**
 * Type Marshaling System - Critical for TTS audio stream compatibility
 *
 * Converts GraalVM JavaScript values to safe Java types, preventing crashes
 * when plugins return audio data, collections, or numbers.
 */
object ScriptValueUtils {
    /**
     * Converts a GraalVM Value to an appropriate Java type with defensive casting.
     *
     * @param value Polyglot value from JavaScript execution
     * @return Java object (byte[], InputStream, List, Map, Number, etc.) or null
     */
    fun toJavaType(value: Value): Any? {
        if (value.isNull) return null
        
        return when {
            // Primitive types
            value.isBoolean -> value.asBoolean()
            value.isNumber -> safeNumberConversion(value)
            value.isString -> value.asString()
            
            // Audio stream data - CRITICAL for TTS plugins
            value.hasArrayElements() -> convertArray(value)
            value.isProxyObject && value.asProxyObject() is ProxyArray -> convertProxyArray(value.asProxyObject() as ProxyArray)
            value.isProxyObject && value.asProxyObject() is ProxyObject -> convertProxyObject(value.asProxyObject() as ProxyObject)
            
            // Native Java objects passed through
            value.isHostObject -> value.asHostObject()
            
            // Functions remain as GraalVM values for callback support
            value.canExecute() -> value
            
            // Default: keep as GraalVM value
            else -> value
        }
    }
    
    /**
     * Safely converts JavaScript Number to Java numeric type.
     * Prevents Double->Integer overflow and precision loss.
     */
    private fun safeNumberConversion(value: Value): Number {
        val doubleVal = value.asDouble()
        
        // If value looks like an integer and fits in Int range
        if (doubleVal.isFinite() && doubleVal % 1.0 == 0.0) {
            val longVal = doubleVal.toLong()
            return when {
                longVal in Int.MIN_VALUE..Int.MAX_VALUE -> longVal.toInt()
                else -> longVal
            }
        }
        
        // Return as Double for floating-point values
        return doubleVal
    }
    
    /**
     * Converts GraalVM array-like value to Java collection or byte array.
     * Prioritizes byte[] conversion for audio data.
     */
    private fun convertArray(value: Value): Any {
        val size = value.arraySize.toInt()
        
        // Check if this appears to be byte array (audio data)
        if (size > 0) {
            val firstElem = value.getArrayElement(0)
            if (firstElem.isNumber) {
                // Try to extract as byte[]
                val byteArray = ByteArray(size)
                var allBytes = true
                
                for (i in 0 until size) {
                    val elem = value.getArrayElement(i)
                    if (elem.isNumber) {
                        val num = elem.asDouble()
                        if (num in Byte.MIN_VALUE.toDouble()..Byte.MAX_VALUE.toDouble()) {
                            byteArray[i] = num.toInt().toByte()
                        } else {
                            allBytes = false
                            break
                        }
                    } else {
                        allBytes = false
                        break
                    }
                }
                
                if (allBytes) {
                    return byteArray
                }
                
                // Try as int[] (common for typed arrays)
                val intArray = IntArray(size)
                var allInts = true
                
                for (i in 0 until size) {
                    val elem = value.getArrayElement(i)
                    if (elem.isNumber) {
                        intArray[i] = elem.asInt()
                    } else {
                        allInts = false
                        break
                    }
                }
                
                if (allInts) {
                    return intArray
                }
            }
        }
        
        // Fallback: convert to List<Any?>
        return (0 until size).map { i ->
            toJavaType(value.getArrayElement(i))
        }
    }
    
    /**
     * Converts ProxyArray to Java List.
     */
    private fun convertProxyArray(proxy: ProxyArray): List<Any?> {
        return (0 until proxy.size).map { i ->
            toJavaType(Value.asValue(proxy.get(i)))
        }
    }
    
    /**
     * Converts ProxyObject to Java Map<String, Any?>.
     */
    private fun convertProxyObject(proxy: ProxyObject): Map<String, Any?> {
        return proxy.memberKeys.associateWith { key ->
            toJavaType(Value.asValue(proxy.getMember(key)))
        }
    }
    
    /**
     * CRITICAL: Converts any JavaScript audio data to InputStream.
     * Supports: byte[], int[], ByteBuffer, Value with array elements, etc.
     *
     * @param audioData JavaScript return value from plugin's getAudio()
     * @return InputStream ready for TTS playback
     * @throws IllegalArgumentException if conversion fails
     */
    fun toAudioInputStream(audioData: Any?): InputStream {
        return ByteArrayInputStream(asByteArray(audioData))
    }

    /**
     * CRITICAL: Converts any JavaScript audio data to byte array.
     * Supports: byte[], int[], ByteBuffer, Value with array elements, etc.
     *
     * This is the core conversion function for TTS audio data handling.
     * GraalJS may wrap audio data in various formats depending on how the plugin
     * constructs the response.
     *
     * @param audioData JavaScript return value from plugin's getAudio()
     * @return ByteArray ready for TTS playback
     * @throws IllegalArgumentException if conversion fails
     */
    fun asByteArray(audioData: Any?): ByteArray {
        if (audioData == null) {
            throw IllegalArgumentException("Audio data is null")
        }

        return when (audioData) {
            is ByteArray -> audioData
            is ByteBuffer -> audioData.array()
            is IntArray -> {
                // Convert IntArray to ByteArray (4 bytes per int)
                ByteArray(audioData.size * 4).apply {
                    ByteBuffer.wrap(this).asIntBuffer().put(audioData)
                }
            }
            is ShortArray -> {
                // Convert ShortArray to ByteArray (2 bytes per short)
                ByteArray(audioData.size * 2).apply {
                    ByteBuffer.wrap(this).asShortBuffer().put(audioData)
                }
            }
            is CharArray -> {
                // Convert CharArray to ByteArray (2 bytes per char)
                ByteArray(audioData.size * 2).apply {
                    ByteBuffer.wrap(this).asCharBuffer().put(audioData)
                }
            }
            is Value -> {
                if (audioData.hasArrayElements()) {
                    convertValueArrayToByteArray(audioData)
                } else if (audioData.isHostObject) {
                    // Recursively convert host object
                    asByteArray(audioData.asHostObject<Any?>())
                } else {
                    throw IllegalArgumentException("Unsupported GraalVM value for audio: $audioData (type: ${audioData.toString()})")
                }
            }
            else -> throw IllegalArgumentException("Unsupported audio data type: ${audioData.javaClass.name}")
        }
    }

    /**
     * Converts a GraalVM Value with array elements to ByteArray.
     * Handles different number types (Byte, Short, Int, Float, Double) and converts appropriately.
     */
    private fun convertValueArrayToByteArray(value: Value): ByteArray {
        val size = value.arraySize.toInt()
        if (size == 0) return ByteArray(0)

        // Check the type of the first element to determine conversion strategy
        val firstElem = value.getArrayElement(0)

        return when {
            // If first element is a valid byte value, treat entire array as byte[]
            firstElem.isNumber && firstElem.asDouble().toInt().toByte().toInt().toLong() == firstElem.asLong() -> {
                val byteArray = ByteArray(size)
                for (i in 0 until size) {
                    val elem = value.getArrayElement(i)
                    if (!elem.isNumber) {
                        throw IllegalArgumentException("Array element at index $i is not a number: $elem")
                    }
                    val num = elem.asDouble()
                    // Clamp to byte range
                    byteArray[i] = num.toInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
                }
                byteArray
            }
            // If numbers exceed byte range, treat as IntArray then convert
            else -> {
                val intArray = IntArray(size)
                for (i in 0 until size) {
                    val elem = value.getArrayElement(i)
                    if (!elem.isNumber) {
                        throw IllegalArgumentException("Array element at index $i is not a number: $elem")
                    }
                    intArray[i] = elem.asInt()
                }
                // Convert IntArray to ByteArray
                ByteArray(intArray.size * 4).apply {
                    ByteBuffer.wrap(this).asIntBuffer().put(intArray)
                }
            }
        }
    }
    
    /**
     * Converts Java Map to JavaScript object proxy.
     */
    fun toJsObject(map: Map<String, Any?>): ProxyObject {
        return ProxyObject.fromMap(map.mapValues { toJsValue(it.value) })
    }
    
    /**
     * Converts Java List to JavaScript array proxy.
     */
    fun toJsArray(list: List<Any?>): ProxyArray {
        return object : ProxyArray {
            override fun get(index: Long): Any? {
                return toJsValue(list[index.toInt()])
            }
            
            override fun set(index: Long, value: Any?) {
                throw UnsupportedOperationException("Read-only proxy array")
            }
            
            override fun getSize(): Long {
                return list.size.toLong()
            }
        }
    }
    
    /**
     * Converts arbitrary Java value to JavaScript-compatible value.
     * Public access for GraalScriptEngine.
     */
    fun toJsValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String -> value
            is Number -> value
            is Boolean -> value
            is Map<*, *> -> toJsObject(value as Map<String, Any?>)
            is List<*> -> toJsArray(value as List<Any?>)
            else -> value
        }
    }
    
    /**
     * Type-safe cast with descriptive error message.
     */
    inline fun <reified T> cast(value: Any?, context: String = ""): T {
        return try {
            value as T
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "Type cast failed in $context. Expected ${T::class.java}, got ${value?.javaClass ?: "null"}"
            )
        }
    }
}