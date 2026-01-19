package com.github.jing332.script.runtime

import com.github.jing332.script.BackstageWebView
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class GraalJSGlobalWebview {
    companion object {
        // 🟡 优化：添加 30 秒超时保护，避免 WebView 加载失败时永久阻塞
        private const val WEBVIEW_TIMEOUT_MS = 30000L

        fun loadUrl(url: String, headers: Map<CharSequence, CharSequence>? = null, script: String? = null): String? {
            val js = script ?: BackstageWebView.JS
            val webview = BackstageWebView(
                BackstageWebView.Payload.Url(url),
                headerMap = headers?.map { it.key.toString() to it.value.toString() }?.toMap()
                    ?: emptyMap(),
                js = js,
            )
            val ret = runBlocking {
                withTimeout(WEBVIEW_TIMEOUT_MS) {
                    webview.getHtmlResponse()
                }
            }
            ret.onSuccess { return it }.onFailure {
                if (it is BackstageWebView.Error.E)
                    throw RuntimeException(it.description)
            }
            return null
        }

        fun loadHtml(html: String, headers: Map<CharSequence, CharSequence>? = null, script: String? = null): String? {
            val js = script ?: BackstageWebView.JS
            val webview = BackstageWebView(
                BackstageWebView.Payload.Data(html),
                headerMap = headers?.map { it.key.toString() to it.value.toString() }?.toMap()
                    ?: emptyMap(),
                js = js
            )
            val ret = runBlocking {
                withTimeout(WEBVIEW_TIMEOUT_MS) {
                    webview.getHtmlResponse()
                }
            }
            ret.onSuccess { return it }.onFailure {
                if (it is BackstageWebView.Error.E)
                    throw RuntimeException(it.description)
            }
            return null
        }
    }
}
