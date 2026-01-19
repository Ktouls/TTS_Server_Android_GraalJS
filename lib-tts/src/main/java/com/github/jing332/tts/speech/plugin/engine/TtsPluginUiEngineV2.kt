package com.github.jing332.tts.speech.plugin.engine

import android.content.Context
import android.util.Log // 👈 使用原生 Log 替代 KotlinLogging
import android.widget.LinearLayout
import com.github.jing332.common.utils.dp
import com.github.jing332.common.utils.toCountryFlagEmoji
import com.github.jing332.database.entities.plugin.Plugin
import org.graalvm.polyglot.Value
import java.util.Locale

class TtsPluginUiEngineV2(context: Context, plugin: Plugin) : TtsPluginEngineV2(context, plugin) {
    companion object {
        private const val TAG = "TtsPluginUiEngineV2"

        const val FUNC_SAMPLE_RATE = "getAudioSampleRate"
        const val FUNC_IS_NEED_DECODE = "isNeedDecode"

        const val FUNC_LOCALES = "getLocales"
        const val FUNC_VOICES = "getVoices"

        const val FUNC_ON_LOAD_UI = "onLoadUI"
        const val FUNC_ON_LOAD_DATA = "onLoadData"
        const val FUNC_ON_VOICE_CHANGED = "onVoiceChanged"

        const val OBJ_UI_JS = "EditorJS"
    }

    fun dp(px: Int): Int {
        return px.dp
    }

    private val editUiJsObject: Value by lazy {
        engine.getValue(OBJ_UI_JS)
            ?: throw IllegalStateException("$OBJ_UI_JS not found")
    }

    override fun execute(script: String): Any? {
        return super.execute(PackageImporter.default + script)
    }


    fun getSampleRate(locale: String, voice: String): Int? {
        runtime.console.debug("getSampleRate($locale, $voice)")

        return engine.invokeMethod(
            editUiJsObject,
            FUNC_SAMPLE_RATE,
            locale,
            voice
        )?.run {
            return if (this is Int) this
            else (this as Double).toInt()
        }
    }

    fun isNeedDecode(locale: String, voice: String): Boolean {
        runtime.console.debug("isNeedDecode($locale, $voice)")

        return try {
            engine.invokeMethod(editUiJsObject, FUNC_IS_NEED_DECODE, locale, voice)?.run {
                if (this is Boolean) this
                else (this as Double).toInt() == 1
            } ?: true
        } catch (_: NoSuchMethodException) {
            true
        }
    }

    fun getLocales(): Map<String, String> {
        val result = engine.invokeMethod(editUiJsObject, FUNC_LOCALES)
        return when (result) {
            is List<*> -> result.associate {
                val locale = Locale.forLanguageTag(it.toString())
                val displayName = locale.country.toCountryFlagEmoji() + " " + locale.displayName
                it.toString() to displayName
            }

            is Map<*, *> -> {
                result.map { (key, value) ->
                    key.toString() to value.toString()
                }.toMap()
            }

            is Value -> {
                if (!result.hasMembers()) emptyMap()
                else {
                    val map = mutableMapOf<String, String>()
                    for (key in result.memberKeys) {
                        val value = result.getMember(key)
                        map[key] = value.toString()
                    }
                    map
                }
            }

            else -> emptyMap()
        }
    }

    fun getVoices(locale: String): List<Voice> {
        return engine.invokeMethod(editUiJsObject, FUNC_VOICES, locale).run {
            when (this) {
                is Value -> {
                    if (!this.hasMembers()) emptyList()
                    else {
                        val result = mutableListOf<Voice>()
                        for (key in this.memberKeys) {
                            val value = this.getMember(key)
                            var icon: String? = null
                            var name: String = ""

                            if (value.isString) {
                                name = value.asString()
                            } else if (value.hasMembers()) {
                                icon = value.getMember("iconUrl")?.asString()
                                    ?: value.getMember("icon")?.asString()
                                name = value.getMember("name")?.asString() ?: ""
                            }

                            result.add(Voice(key, name, icon))
                        }
                        result
                    }
                }

                else -> emptyList()
            }
        }
    }

    fun onLoadData() {
        runtime.console.debug("onLoadData()...")

        try {
            engine.invokeMethod(editUiJsObject, FUNC_ON_LOAD_DATA)
        } catch (_: NoSuchMethodException) {
        }
    }

    fun onLoadUI(context: Context, container: LinearLayout) {
        runtime.console.debug("onLoadUI()...")
        try {
            engine.invokeMethod(
                editUiJsObject,
                FUNC_ON_LOAD_UI,
                context,
                container
            )
        } catch (_: NoSuchMethodException) {
        }
    }

    fun onVoiceChanged(locale: String, voice: String) {
        runtime.console.debug("onVoiceChanged($locale, $voice)")

        try {
            engine.invokeMethod(
                editUiJsObject,
                FUNC_ON_VOICE_CHANGED,
                locale,
                voice
            )
        } catch (_: NoSuchMethodException) {
        }
    }

    data class Voice(val id: String, val name: String, val icon: String? = null)
}
