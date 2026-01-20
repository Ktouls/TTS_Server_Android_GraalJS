@file:OptIn(ExperimentalUuidApi::class)

package com.github.jing332.script.runtime

import com.github.jing332.script.engine.GraalJsRuntime
import com.github.jing332.script.exception.runScriptCatching
import org.graalvm.polyglot.Value
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GraalJS 版本的 UUID 工具对象
 * 原本继承 Global -> ScriptableObject，现改为纯 Kotlin 类
 */
class GlobalUUID {
    companion object {
        const val NAME = "UUID"

        /**
         * 初始化 UUID 对象到 GraalJS 作用域
         */
        @JvmStatic
        fun init(bindings: Value, sealed: Boolean) {
            bindings.putMember(NAME, GlobalUUID())
        }
    }

    /**
     * 生成随机 UUID v4
     */
    fun v4(): String {
        return Uuid.random().toString()
    }

    /**
     * 将字节数组转换为 UUID 字符串
     * @param bytes 16字节的 UUID 字节数组
     */
    fun stringify(bytes: Any): String = runScriptCatching {
        val byteArray = when (bytes) {
            is ByteArray -> bytes
            is Value -> {
                // 处理 GraalJS Value 类型
                val javaObj = GraalJsRuntime.convertValue(bytes)
                if (javaObj is ByteArray) {
                    javaObj
                } else if (bytes.hasArrayElements()) {
                    val size = bytes.arraySize.toInt()
                    val arr = ByteArray(size)
                    for (i in 0 until size) {
                        arr[i] = bytes.getArrayElement(i).asInt().toByte()
                    }
                    arr
                } else {
                    throw IllegalArgumentException("Unsupported bytes type")
                }
            }
            else -> throw IllegalArgumentException("Unsupported bytes type: ${bytes.javaClass.name}")
        }

        Uuid.fromByteArray(byteArray).toString()
    }

    /**
     * 将 UUID 字符串解析为字节数组
     * @param str UUID 字符串
     * @return 16字节的字节数组
     */
    fun parse(str: Any): ByteArray = runScriptCatching {
        val uuidStr = str.toString()
        Uuid.parse(uuidStr).toByteArray()
    }

    /**
     * 验证 UUID 字符串是否有效
     * @param str UUID 字符串
     * @return 是否有效
     */
    fun validate(str: Any): Boolean = runScriptCatching {
        try {
            val uuidStr = str.toString()
            Uuid.parse(uuidStr)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}