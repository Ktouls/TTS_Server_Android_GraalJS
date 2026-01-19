package com.github.jing332.script.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.concurrent.TimeUnit

class GraalJSWebSocket(
    private val context: Context,
    url: String = "",
    headers: Map<CharSequence, CharSequence> = emptyMap(),
) : ProxyObject {
    companion object {
        val logger = KotlinLogging.logger("GraalJSWebSocket")

        const val WS_CONNECTING = 0
        const val WS_OPEN = 1
        const val WS_CLOSING = 2
        const val WS_CLOSED = 3

        private val client by lazy {
            OkHttpClient.Builder()
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    private var readyState: Int = WS_CLOSED
    private var ws: WebSocket? = null
    private val event = GraalJSEventTarget(context)

    init {
        val req = Request.Builder().url(url).apply {
            for (header in headers) {
                addHeader(header.key.toString(), header.value.toString())
            }
        }.build()

        logger.trace { "connecting to $url" }
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                readyState = WS_OPEN
                val res = GraalJSNativeResponse(response)
                event.emit("open", res)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                event.emit("message", text)
                event.emit("text", text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val buffer = GraalJSBuffer.fromByteArray(bytes.toByteArray())
                event.emit("message", buffer)
                event.emit("binary", buffer)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                readyState = WS_CLOSING
                ws?.close(code, reason) // call onClosed
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                readyState = WS_CLOSED
                event.emit("close", code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                readyState = WS_CLOSED
                val res = response?.let { GraalJSNativeResponse(it) }
                event.emit("error", t.message ?: "", res)
            }
        })
    }

    private fun sendBytes(bytes: ByteArray): Boolean {
        logger.trace { "send binary message: ${bytes.size} bytes" }
        return ws?.send(bytes.toByteString()) == true
    }

    override fun getMember(key: String): Any? {
        return when (key) {
            "CONNECTING" -> WS_CONNECTING
            "OPEN" -> WS_OPEN
            "CLOSING" -> WS_CLOSING
            "CLOSED" -> WS_CLOSED
            "readyState" -> readyState
            "send" -> context.asValue { args: Array<Value> ->
                when (val arg0 = args[0]) {
                    is String -> {
                        logger.trace { "send text message: $arg0" }
                        ws?.send(arg0) == true
                    }
                    else -> {
                        val bytes = when {
                            arg0.hasBuffer() -> arg0.asBuffer()
                            arg0.hasArrayElements() -> ByteArray(arg0.arraySize.toInt()) { i ->
                                arg0.getArrayElement(i.toLong()).asByte()
                            }
                            else -> null
                        }
                        bytes?.let { sendBytes(it) } ?: false
                    }
                }
            }
            "close" -> context.asValue { args: Array<Value> ->
                val code = if (args.isNotEmpty()) args[0].asInt() else 1000
                val reason = if (args.size > 1) args[1].asString() else ""
                logger.trace { "closing: $code, $reason" }
                ws?.close(code, reason) ?: false
            }
            "cancel" -> context.asValue {
                ws?.cancel()
                null
            }
            "on" -> ProxyExecutable { args ->
                val eventName = args[0].asString()
                val function = args[1]
                if (function.canExecute()) {
                    event.functions[eventName] = function
                }
                null
            }
            "addEventListener" -> ProxyExecutable { args ->
                val eventName = args[0].asString()
                val function = args[1]
                if (function.canExecute()) {
                    event.functions[eventName] = function
                }
                null
            }
            else -> event.functions[key]
        }
    }

    override fun hasMember(key: String): Boolean {
        return key in listOf(
            "CONNECTING", "OPEN", "CLOSING", "CLOSED",
            "readyState", "send", "close", "cancel",
            "on", "addEventListener"
        ) || key in event.functions.keys
    }

    override fun getMemberKeys(): Array<String> {
        val keys = mutableListOf(
            "CONNECTING", "OPEN", "CLOSING", "CLOSED",
            "readyState", "send", "close", "cancel",
            "on", "addEventListener"
        )
        keys.addAll(event.functions.keys)
        return keys.toTypedArray()
    }

    override fun putMember(key: String?, value: Value?) {
        // Allow setting event handlers
        if (key != null && value != null && value.canExecute()) {
            event.functions[key] = value
        }
    }
}
