package com.github.jing332.tts.speech.plugin.engine

import android.content.Context
import com.drake.net.Net
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.script.engine.GraalJSScriptEngine
import com.github.jing332.script.runtime.GraalJSNativeResponse
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.simple.GraalCompatScriptRuntime
import com.github.jing332.script.source.toScriptSource
import com.github.jing332.tts.speech.EmptyInputStream
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.graalvm.polyglot.Value
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

open class TtsPluginEngineV2(val context: Context, var plugin: Plugin) {
    companion object {
        const val OBJ_PLUGIN_JS = "PluginJS"
        const val FUNC_GET_AUDIO = "getAudio"
        const val FUNC_GET_AUDIO_V2 = "getAudioV2"
        const val FUNC_ON_LOAD = "onLoad"
        const val FUNC_ON_STOP = "onStop"
    }

    var console: Console
        get() = engine.runtime.console
        set(value) { engine.runtime.console = value }

    protected val ttsrv = TtsEngineContext(PluginTtsSource(), plugin.userVars, context, plugin.pluginId)
    val runtime = GraalCompatScriptRuntime(ttsrv)
    var source: PluginTtsSource
        get() = ttsrv.tts
        set(value) { ttsrv.tts = value }

    protected val pluginJsObj: Value
        get() = engine.getValue(OBJ_PLUGIN_JS) ?: throw IllegalStateException("Object `$OBJ_PLUGIN_JS` not found")

    protected var engine: GraalJSScriptEngine = GraalJSScriptEngine(runtime)

    open protected fun execute(script: String): Any? = engine.execute(script.toScriptSource(sourceName = plugin.pluginId))

    fun eval() {
        execute(plugin.code)
        pluginJsObj.apply {
            plugin.name = getMember("name")?.asString() ?: ""
            plugin.pluginId = getMember("id")?.asString() ?: ""
            plugin.author = getMember("author")?.asString() ?: ""
            plugin.iconUrl = getMember("iconUrl")?.asString() ?: ""
            plugin.defVars = try {
                val varsValue = getMember("vars")
                if (varsValue != null && varsValue.hasMembers()) {
                    varsValue.memberKeys.associateWith { key ->
                        val v = varsValue.getMember(key)
                        if (v.hasMembers()) {
                            v.memberKeys.associateWith { k -> v.getMember(k).toString() }
                        } else {
                            emptyMap()
                        }
                    }
                } else {
                    emptyMap()
                }
            } catch (_: Exception) { emptyMap() }
            plugin.version = try { getMember("version")?.asInt() ?: -1 } catch (e: Exception) { -1 }
        }
    }

    fun onLoad(): Any? = runCatching { engine.invokeMethod(pluginJsObj, FUNC_ON_LOAD) }.getOrNull()
    fun onStop(): Any? = runCatching { engine.invokeMethod(pluginJsObj, FUNC_ON_STOP) }.getOrNull()

    private fun handleAudioResult(result: Any?): InputStream? {
        if (result == null) return null
        return when (result) {
            is ByteArray -> ByteArrayInputStream(result)
            is InputStream -> result
            is GraalJSNativeResponse -> {
                if (result.rawResponse?.isSuccessful == false) throw RuntimeException("HTTP Error: ${result.rawResponse?.code}")
                result.rawResponse?.body?.byteStream()
            }
            is Value -> {
                when {
                    result.hasArrayElements() -> {
                        val bytes = ByteArray(result.arraySize.toInt()) { i ->
                            result.getArrayElement(i.toLong()).asByte()
                        }
                        ByteArrayInputStream(bytes)
                    }
                    result.isString -> {
                        val str = result.asString()
                        if (str.startsWith("http")) {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(300, TimeUnit.SECONDS)
                                .readTimeout(300, TimeUnit.SECONDS)
                                .build()
                            val resp = client.newCall(Request.Builder().url(str).build()).execute()
                            if (!resp.isSuccessful) throw RuntimeException("URL Fetch Error: ${resp.code}")
                            resp.body?.byteStream()
                        } else throw IllegalStateException(str)
                    }
                    else -> throw IllegalArgumentException("Unsupported return type: ${result.javaClass.name}")
                }
            }
            else -> throw IllegalArgumentException("Unsupported return type: ${result.javaClass.name}")
        }
    }

    private val mMutex by lazy { Mutex() }

    private suspend fun getAudioV2(request: Map<String, Any>): InputStream {
        val ins = JsBridgeInputStream()
        val callback = ins.getCallback(mMutex) 
        val result = runInterruptible {
            engine.invokeMethod(pluginJsObj, FUNC_GET_AUDIO_V2, request, callback)
                ?: throw NoSuchMethodException("getAudioV2() not found")
        }
        return handleAudioResult(result) ?: ins
    }

    suspend fun getAudio(text: String, locale: String, voice: String, rate: Float = 1f, volume: Float = 1f, pitch: Float = 1f): InputStream {
        val r = (rate * 50f).toInt(); val v = (volume * 50f).toInt(); val p = (pitch * 50f).toInt()
        
        // 🛠️ 关键：去掉了 try-catch 兜底，不再返回 EmptyInputStream
        // 一旦出错（暗号拦截或超时），抛出异常让 Service 处理，彻底根治 Bad audio format 0
        val result = try {
            runInterruptible {
                engine.invokeMethod(pluginJsObj, FUNC_GET_AUDIO, text, locale, voice, r, v, p)
            }
        } catch (_: NoSuchMethodException) {
            return getAudioV2(mapOf("text" to text, "locale" to locale, "voice" to voice, "rate" to r, "speed" to r, "volume" to v, "pitch" to p))
        }
        return handleAudioResult(result) ?: throw RuntimeException("Synthesis Result is Empty")
    }
}
