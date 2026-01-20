package com.github.jing332.script.runtime

import com.github.jing332.script.exception.runScriptCatching
import okhttp3.Response
import java.io.InputStream

/**
 * GraalJS 版本的 HTTP 响应对象
 * 原本继承 ScriptableObject()，现改为普通对象
 * GraalJS 通过 HostAccess.ALL 自动暴露属性
 */
class NativeResponse(val rawResponse: Response?) {

    companion object {
        const val CLASS_NAME = "Response"

        /**
         * 从 OkHttp Response 创建 NativeResponse
         */
        fun of(response: Response?): NativeResponse = NativeResponse(response)
    }

    /**
     * HTTP 状态码
     */
    val status: Int
        get() = rawResponse?.code ?: 0

    /**
     * HTTP 状态文本
     */
    val statusText: String
        get() = rawResponse?.message ?: ""

    /**
     * 响应头
     */
    val headers: Map<String, String>
        get() = rawResponse?.headers?.toMap() ?: emptyMap()

    /**
     * 请求 URL
     */
    val url: String
        get() = rawResponse?.request?.url?.toString() ?: ""

    /**
     * 是否重定向
     */
    val redirected: Boolean
        get() = rawResponse?.isRedirect == true

    /**
     * 请求是否成功 (状态码 2xx)
     */
    val ok: Boolean
        get() = rawResponse?.isSuccessful == true

    /**
     * 响应体流
     */
    val body: InputStream?
        get() = rawResponse?.body?.byteStream()

    /**
     * 响应体作为 JSON 对象
     */
    fun json(): Any = runScriptCatching {
        val str = rawResponse?.body?.string() ?: return@runScriptCatching ""
        // 使用 Gson 解析 JSON
        com.google.gson.Gson().fromJson(str, Any::class.java)
    }

    /**
     * 响应体作为文本
     */
    fun text(): Any = runScriptCatching {
        rawResponse?.body?.string() ?: ""
    }

    /**
     * 响应体作为字节数组
     */
    fun bytes(): ByteArray = runScriptCatching {
        rawResponse?.body?.bytes() ?: ByteArray(0)
    }

    override fun toString(): String = "NativeResponse($status $url)"
}
