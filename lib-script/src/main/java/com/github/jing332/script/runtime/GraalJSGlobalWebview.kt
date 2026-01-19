package com.github.jing332.script.runtime

import com.github.jing332.script.BackstageWebView
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.runBlocking

class GraalJSGlobalWebview {
    companion object {
        fun loadUrl(url: String, headers: Map<CharSequence, CharSequence>? = null, script: String? = null): String? {
            val js = script ?: BackstageWebView.JS
            val webview = BackstageWebView(
                BackstageWebView.Payload.Url(url),
                headerMap = headers?.map { it.key.toString() to it.value.toString() }?.toMap()
                    ?: emptyMap(),
                js = js,
            )
            val ret = runBlocking { webview.getHtmlResponse() }
            return ret.onSuccess { it }.onFailure {
                if (it is BackstageWebView.Error.E)
                    throw RuntimeException(it.description)
            }.getOrNull()
        }

        fun loadHtml(html: String, headers: Map<CharSequence, CharSequence>? = null, script: String? = null): String? {
            val js = script ?: BackstageWebView.JS
            val webview = BackstageWebView(
                BackstageWebView.Payload.Data(html),
                headerMap = headers?.map { it.key.toString() to it.value.toString() }?.toMap()
                    ?: emptyMap(),
                js = js
            )
            val ret = runBlocking { webview.getHtmlResponse() }
            return ret.onSuccess { it }.onFailure {
                if (it is BackstageWebView.Error.E)
                    throw RuntimeException(it.description)
            }.getOrNull()
        }
    }
}
