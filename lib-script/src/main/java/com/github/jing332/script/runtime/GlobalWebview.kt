package com.github.jing332.script.runtime

import com.github.jing332.script.BackstageWebView
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.runBlocking
import org.graalvm.polyglot.Value

/**
 * GraalJS 版本的 WebView 对象
 * 原本继承 Global -> ScriptableObject，现改为纯 Kotlin 类
 * 提供后台 WebView 渲染功能
 */
class GlobalWebview {
    companion object {
        const val NAME = "webview"

        /**
         * 初始化 WebView 对象到 GraalJS 作用域
         */
        @JvmStatic
        fun init(bindings: Value, sealed: Boolean) {
            bindings.putMember(NAME, GlobalWebview())
        }
    }

    /**
     * 加载 URL 并渲染
     * @param url 要加载的 URL
     * @param headers 可选的请求头
     * @param script 可选的注入脚本
     * @return 渲染后的 HTML 内容
     */
    fun loadUrl(url: Any, headers: Any? = null, script: Any? = null): String {
        val urlStr = url.toString()
        val headerMap = convertHeaders(headers)
        val scriptStr = script?.toString() ?: BackstageWebView.JS

        val webview = BackstageWebView(
            BackstageWebView.Payload.Url(urlStr),
            headerMap = headerMap,
            js = scriptStr,
        )

        val ret = runBlocking { webview.getHtmlResponse() }
        return ret.onSuccess {
            it
        }.onFailure {
            if (it is BackstageWebView.Error.E) {
                throw RuntimeException(it.description)
            }
            throw RuntimeException("WebView load failed", it)
        }.getOrThrow()
    }

    /**
     * 加载 HTML 并渲染
     * @param html HTML 内容
     * @param headers 可选的请求头
     * @param script 可选的注入脚本
     * @return 渲染后的 HTML 内容
     */
    fun loadHtml(html: Any, headers: Any? = null, script: Any? = null): String {
        val htmlStr = html.toString()
        val headerMap = convertHeaders(headers)
        val scriptStr = script?.toString() ?: BackstageWebView.JS

        val webview = BackstageWebView(
            BackstageWebView.Payload.Data(htmlStr),
            headerMap = headerMap,
            js = scriptStr
        )

        val ret = runBlocking { webview.getHtmlResponse() }
        return ret.onSuccess {
            it
        }.onFailure {
            if (it is BackstageWebView.Error.E) {
                throw RuntimeException(it.description)
            }
            throw RuntimeException("WebView load failed", it)
        }.getOrThrow()
    }

    /**
     * 转换请求头为 Map 格式
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertHeaders(headers: Any?): Map<String, String> {
        if (headers == null) return emptyMap()

        return when (headers) {
            is Map<*, *> -> {
                headers.mapKeys { it.key?.toString() ?: "" }
                    .mapValues { it.value?.toString() ?: "" }
            }
            is Value -> {
                // 处理 GraalJS Value 类型
                val javaObj = headers.asHostObject<Map<*, *>>()
                javaObj?.mapKeys { it.key?.toString() ?: "" }
                    ?.mapValues { it.value?.toString() ?: "" }
                    ?: emptyMap()
            }
            else -> emptyMap()
        }
    }
}

