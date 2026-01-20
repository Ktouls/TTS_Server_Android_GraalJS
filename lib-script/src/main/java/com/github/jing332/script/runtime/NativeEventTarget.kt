package com.github.jing332.script.runtime

import com.github.jing332.common.utils.firstCharUpperCase
import io.github.oshai.kotlinlogging.KotlinLogging
import org.graalvm.polyglot.Value

/**
 * GraalJS 版本的事件目标对象
 * 原本使用 Rhino 的 Function，现改为 GraalVM 的 Value
 */
class NativeEventTarget {
    companion object {
        val logger = KotlinLogging.logger("NativeEventTarget")
    }

    private val functions = hashMapOf<String, Value>()

    /**
     * 添加事件监听器
     * @param eventName 事件名称
     * @param callback JavaScript 回调函数 (GraalVM Value)
     */
    fun on(eventName: String, callback: Value) {
        if (callback.canExecute()) {
            functions[eventName] = callback
        }
    }

    /**
     * 添加事件监听器（别名）
     */
    fun addEventListener(eventName: String, callback: Value) {
        on(eventName, callback)
    }

    /**
     * 触发事件
     * @param eventName 事件名称
     * @param args 传递给回调的参数
     */
    fun emit(eventName: String, vararg args: Any?) {
        val callback = functions[eventName] ?: return

        try {
            callback.execute(*args)
        } catch (e: Exception) {
            logger.error(e) { "emit error: $eventName" }

            // 触发错误回调
            runCatching {
                functions["error"]?.execute(e)
            }
        }
    }

    /**
     * 移除事件监听器
     */
    fun off(eventName: String) {
        functions.remove(eventName)
    }

    /**
     * 清空所有监听器
     */
    fun removeAllListeners() {
        functions.clear()
    }
}