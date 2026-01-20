package com.github.jing332.tts.speech.plugin.engine

import android.content.Context
import com.drake.net.Net
import com.github.jing332.database.entities.plugin.Plugin
import com.github.jing332.database.entities.systts.source.PluginTtsSource
import com.github.jing332.script.engine.GraalScriptEngine
import com.github.jing332.script.engine.ScriptValueUtils
import com.github.jing332.script.runtime.NativeResponse
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.simple.CompatScriptRuntime
import com.github.jing332.script.source.toScriptSource
import com.github.jing332.tts.speech.EmptyInputStream
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

    var console: Console = Console()

    protected val ttsrv = TtsEngineContext(PluginTtsSource(), plugin.userVars, context, plugin.pluginId)
    var source: PluginTtsSource
        get() = ttsrv.tts
        set(value) { ttsrv.tts = value }

    protected val pluginJsObj: Any?
        get() = engine.get(OBJ_PLUGIN_JS)

    protected var engine: GraalScriptEngine = GraalScriptEngine(context)

    open protected fun execute(script: String): Any? = engine.execute(script.toScriptSource(sourceName = plugin.pluginId))

    fun eval() {
        execute(plugin.code)
        val obj = pluginJsObj
        if (obj == null) {
            throw IllegalStateException("Object `$OBJ_PLUGIN_JS` not found")
        }
        
        // Extract plugin metadata from the JS object
        plugin.name = engine.get("$OBJ_PLUGIN_JS.name")?.toString() ?: ""
        plugin.pluginId = engine.get("$OBJ_PLUGIN_JS.id")?.toString() ?: ""
        plugin.author = engine.get("$OBJ_PLUGIN_JS.author")?.toString() ?: ""
        plugin.iconUrl = engine.get("$OBJ_PLUGIN_JS.iconUrl")?.toString() ?: ""
        plugin.defVars = try { engine.get("$OBJ_PLUGIN_JS.vars") as? Map<String, Map<String, String>> ?: emptyMap() } catch (_: Exception) { emptyMap() }
        
        // Version conversion - handle both number and string
        val versionValue = engine.get("$OBJ_PLUGIN_JS.version")
        plugin.version = when (versionValue) {
            is Number -> versionValue.toInt()
            is String -> versionValue.toIntOrNull() ?: -1
            else -> -1
        }
    }

    fun onLoad(): Any? = runCatching { engine.invokeFunction("$OBJ_PLUGIN_JS.$FUNC_ON_LOAD") }.getOrNull()
    fun onStop(): Any? = runCatching { engine.invokeFunction("$OBJ_PLUGIN_JS.$FUNC_ON_STOP") }.getOrNull()

    private fun handleAudioResult(result: Any?): InputStream? {
        if (result == null) return null
        
        return try {
            ScriptValueUtils.toAudioInputStream(result)
        } catch (e: IllegalArgumentException) {
            // Fallback for other types (e.g., NativeResponse, URL strings)
            when (result) {
                is NativeResponse -> {
                    if (result.rawResponse?.isSuccessful == false) throw RuntimeException("HTTP Error: ${result.rawResponse?.code}")
                    result.rawResponse?.body?.byteStream()
                }
                is CharSequence -> {
                    val str = result.toString()
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
    }

    private val mMutex by lazy { Mutex() }

    private suspend fun getAudioV2(request: Map<String, Any>): InputStream {
        val ins = JsBridgeInputStream()
        val callback = ins.getCallback(mMutex) 
        val result = runInterruptible {
            engine.invokeFunction("$OBJ_PLUGIN_JS.$FUNC_GET_AUDIO_V2", request, callback)
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
                engine.invokeFunction("$OBJ_PLUGIN_JS.$FUNC_GET_AUDIO", text, locale, voice, r, v, p)
            }
        } catch (_: NoSuchMethodException) {
            return getAudioV2(mapOf("text" to text, "locale" to locale, "voice" to voice, "rate" to r, "speed" to r, "volume" to v, "pitch" to p))
        }
        return handleAudioResult(result) ?: throw RuntimeException("Synthesis Result is Empty")
    }
}