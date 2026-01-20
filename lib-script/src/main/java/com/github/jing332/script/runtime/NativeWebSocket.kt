package com.github.jing332.script.runtime

import com.github.jing332.script.engine.GraalJsRuntime
import com.github.jing332.script.engine.ScriptValueUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.graalvm.polyglot.Value
import java.util.concurrent.TimeUnit

/**
 * GraalJS 版本的 WebSocket 对象
 * 原本继承 IdScriptableObject，现改为纯 Kotlin 类
 */
class NativeWebSocket private constructor(
    private val url: String,
    private val headers: Map<CharSequence, CharSequence>
) {
    companion object {
        const val CONNECTING = 0
        const val OPEN = 1
        const val CLOSING = 2
        const val CLOSED = 3

        val logger = KotlinLogging.logger("NativeWebSocket")
        const val CLASS_NAME = "WebSocket"

        private val client by lazy {
            OkHttpClient.Builder()
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    var readyState: Int = CLOSED
        private set

    private var ws: WebSocket? = null
    private val event = NativeEventTarget()

    /**
     * 创建 WebSocket 实例（从 JS 调用）
     */
    constructor(url: Any, headers: Any? = null) : this(
        url.toString(),
        when (headers) {
            is Map<*, *> -> headers.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
            is Value -> {
                val javaObj = GraalJsRuntime.convertValue(headers)
                if (javaObj is Map<*, *>) {
                    javaObj.mapKeys { it.key?.toString() ?: "" }
                        .mapValues { it.value?.toString() ?: "" }
                } else {
                    emptyMap()
                }
            }
            else -> emptyMap()
        }
    ) {
        connect()
    }

    /**
     * 连接 WebSocket
     */
    private fun connect() {
        readyState = CONNECTING

        val req = Request.Builder().url(url).apply {
            headers.forEach { (key, value) ->
                addHeader(key.toString(), value.toString())
            }
        }.build()

        logger.trace { "connecting to $url" }
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                readyState = OPEN
                val res = NativeResponse(response)
                event.emit("open", res)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                event.emit("message", text)
                event.emit("text", text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val byteArray = bytes.toByteArray()
                event.emit("message", byteArray)
                event.emit("binary", byteArray)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                readyState = CLOSING
                ws?.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                readyState = CLOSED
                event.emit("close", code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                readyState = CLOSED
                val res = response?.let { NativeResponse(it) }
                event.emit("error", t.message ?: "", res)
            }
        })
    }

    /**
     * 发送消息
     */
    fun send(data: Any): Boolean {
        return when (data) {
            is CharSequence -> {
                val text = data.toString()
                logger.trace { "send text message: $text" }
                ws?.send(text) == true
            }
            is ByteArray -> {
                logger.trace { "send binary message: ${data.size} bytes" }
                ws?.send(data.toByteString()) == true
            }
            is Value -> {
                // 处理 GraalJS Value 类型
                val javaObj = ScriptValueUtils.toJavaType(data)
                when (javaObj) {
                    is ByteArray -> send(javaObj)
                    is String -> send(javaObj)
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * 关闭 WebSocket
     */
    fun close(code: Any? = null, reason: Any? = null): Boolean {
        val closeCode = code?.toString()?.toIntOrNull() ?: 1000
        val closeReason = reason?.toString() ?: ""
        logger.trace { "closing: $closeCode, $closeReason" }
        return ws?.close(closeCode, closeReason) ?: false
    }

    /**
     * 取消连接
     */
    fun cancel() {
        ws?.cancel()
    }

    /**
     * 添加事件监听器
     */
    fun on(eventName: String, callback: Value) {
        event.on(eventName, callback)
    }

    /**
     * 添加事件监听器（别名）
     */
    fun addEventListener(eventName: String, callback: Value) {
        event.addEventListener(eventName, callback)
    }

    /**
     * 移除事件监听器
     */
    fun off(eventName: String) {
        event.off(eventName)
    }

    /**
     * 移除所有监听器
     */
    fun removeAllListeners() {
        event.removeAllListeners()
    }



    override fun toString(): String = "WebSocket(readyState=$readyState, url=$url)"
}