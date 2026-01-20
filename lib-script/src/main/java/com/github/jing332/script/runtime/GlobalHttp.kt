package com.github.jing332.script.runtime

import android.util.Log
import com.drake.net.Net
import com.github.jing332.script.engine.ScriptValueUtils
import com.github.jing332.script.ensureArgumentsLength
import com.github.jing332.script.exception.runScriptCatching
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.graalvm.polyglot.Value
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GraalJS 版本的全局 HTTP 对象
 * 原本继承 ScriptableObject()，现改为普通对象
 * GraalJS 通过 HostAccess.ALL 自动暴露方法
 */
class GlobalHttp {
    companion object {
        const val NAME = "http"
        private val TAG = "GlobalHttp"
        private val logger = KotlinLogging.logger(TAG)

        private const val MAX_RETRY_COUNTS = 150
        private const val RETRY_INTERVAL_MS = 2000L

        /**
         * 初始化 HTTP 对象到 GraalJS 作用域
         */
        @JvmStatic
        fun init(bindings: Value, sealed: Boolean) {
            bindings.putMember(NAME, GlobalHttp())
        }

        private fun returnErrorResponse(url: String, msg: String): Response {
            return Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message(msg)
                .body(msg.toResponseBody(null))
                .build()
        }

        private fun executeWithRetry(url: String, block: () -> Response): Response {
            var currentRetry = 0
            var lastError: Exception? = null

            while (currentRetry < MAX_RETRY_COUNTS && !Thread.currentThread().isInterrupted) {
                try {
                    val resp = block()
                    if (resp.isSuccessful) return resp
                    else throw RuntimeException("HTTP Code ${resp.code}")
                } catch (e: Exception) {
                    if (e is InterruptedException || Thread.currentThread().isInterrupted) break

                    lastError = e
                    currentRetry++
                    if (currentRetry % 5 == 1) Log.w(TAG, "网络异常, 正在重试 ($currentRetry): ${e.message}")

                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            return returnErrorResponse(url, "Request Interrupted or Failed: ${lastError?.message}")
        }
    }

    /**
     * HTTP GET 请求
     * @param url 请求地址
     * @param headers 可选的请求头 { "key": "value" }
     * @return NativeResponse 对象
     */
    fun get(url: Any, headers: Any? = null): NativeResponse {
        val urlStr = url.toString()
        val headerMap = headers?.let { ScriptValueUtils.toJavaType(it as Value) as? Map<*, *> }

        return runScriptCatching {
            val resp = executeWithRetry(urlStr) {
                Net.get(urlStr) {
                    headerMap?.forEach { setHeader(it.key.toString(), it.value.toString()) }
                }.execute<Response>()
            }
            NativeResponse(resp)
        }
    }

    /**
     * HTTP POST 请求
     * @param url 请求地址
     * @param body 请求体（字符串、Map等）
     * @param headers 可选的请求头
     * @return NativeResponse 对象
     */
    fun post(url: Any, body: Any? = null, headers: Any? = null): NativeResponse {
        val urlStr = url.toString()
        val headerMap = headers?.let { ScriptValueUtils.toJavaType(it as Value) as? Map<*, *> }
        val contentType = (headerMap?.get("Content-Type")?.toString())?.toMediaType()

        return runScriptCatching {
            val resp = executeWithRetry(urlStr) {
                Net.post(urlStr) {
                    headerMap?.forEach { setHeader(it.key.toString(), it.value.toString()) }
                    when (body) {
                        is CharSequence -> this.body = body.toString().toRequestBody(contentType)
                        is Map<*, *> -> this.body = postMultipart("multipart/form-data", body as Map<*, *>).build()
                    }
                }.execute()
            }
            NativeResponse(resp)
        }
    }

    private fun postMultipart(type: String, form: Map<*, *>): MultipartBody.Builder {
        val multipartBody = MultipartBody.Builder().setType(type.toMediaType())
        form.forEach { entry ->
            when (entry.value) {
                is Map<*, *> -> {
                    val filePartMap = entry.value as Map<*, *>
                    val fileName = filePartMap["fileName"]?.toString()
                    val body = filePartMap["body"]
                    val contentType = filePartMap["contentType"]?.toString()?.toMediaType()
                    val mediaType = contentType
                    val requestBody = when (body) {
                        is File -> body.asRequestBody(mediaType)
                        is ByteArray -> body.toRequestBody(mediaType)
                        is String -> body.toRequestBody(mediaType)
                        else -> body.toString().toRequestBody()
                    }
                    multipartBody.addFormDataPart(entry.key.toString(), fileName.toString(), requestBody)
                }
                else -> multipartBody.addFormDataPart(entry.key.toString(), entry.value as String)
            }
        }
        return multipartBody
    }
}
