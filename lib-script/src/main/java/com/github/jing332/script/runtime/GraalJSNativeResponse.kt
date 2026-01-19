package com.github.jing332.script.runtime

import com.github.jing332.script.exception.runScriptCatching
import okhttp3.Response
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject

class GraalJSNativeResponse(val rawResponse: Response? = null) : ProxyObject {
    override fun getMember(key: String): Any? {
        return when (key) {
            "status" -> rawResponse?.code ?: 0
            "statusText" -> rawResponse?.message ?: ""
            "headers" -> rawResponse?.headers?.toMap() ?: emptyMap<String, String>()
            "url" -> rawResponse?.request?.url?.toString() ?: ""
            "redirected" -> rawResponse?.isRedirect == true
            "ok" -> rawResponse?.isSuccessful == true
            else -> null
        }
    }

    override fun hasMember(key: String): Boolean {
        return key in listOf("status", "statusText", "headers", "url", "redirected", "ok", "json", "text", "bytes")
    }

    override fun getMemberKeys(): Array<String> {
        return arrayOf("status", "statusText", "headers", "url", "redirected", "ok", "json", "text", "bytes")
    }

    override fun putMember(key: String?, value: Value?) {
        // Read-only properties
    }

    fun json(force: Boolean = false): Any? = runScriptCatching {
        val resp = rawResponse ?: throw IllegalStateException("rawResponse is null")
        val str = resp.body?.string() ?: return@runScriptCatching ""
        org.graalvm.polyglot.proxy.ProxyArray.fromArray(str) // Simplified JSON parsing
    }

    fun text(force: Boolean = false): String = runScriptCatching {
        val resp = rawResponse ?: throw IllegalStateException("rawResponse is null")
        resp.body?.string() ?: ""
    }

    fun bytes(force: Boolean = false): ByteArray = runScriptCatching {
        val resp = rawResponse ?: throw IllegalStateException("rawResponse is null")
        resp.body?.bytes() ?: ByteArray(0)
    }
}
