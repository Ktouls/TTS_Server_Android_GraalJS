package com.github.jing332.tts_server_android.model.script.speech_rule

import android.content.Context
import com.github.jing332.database.entities.SpeechRule
import com.github.jing332.database.entities.TagsDataMap
import com.github.jing332.database.entities.systts.SpeechRuleInfo

import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.simple.SimpleScriptEngine
import com.github.jing332.script.source.toScriptSource
import com.github.jing332.tts_server_android.R
import org.graalvm.polyglot.Value

class SpeechRuleEngine(
    val context: Context,
    private val rule: SpeechRule
) {
    companion object {
        const val OBJ_JS = "SpeechRuleJS"

        const val FUNC_GET_TAG_NAME = "getTagName"
        const val FUNC_HANDLE_TEXT = "handleText"
        const val FUNC_SPLIT_TEXT = "splitText"

        fun getTagName(context: Context, speechRule: SpeechRule, info: SpeechRuleInfo): String {
            val engine = SpeechRuleEngine(context, speechRule)
            engine.eval()

            val tagName = try {
                engine.getTagName(info.tag, info.tagData)
            } catch (_: NoSuchMethodException) {
                speechRule.tags[info.tag] ?: ""
            }

            return tagName
        }
    }

    val engine = SimpleScriptEngine(context, rule.ruleId)
    var console: Console
        get() = engine.runtime.console
        set(value) {
            engine.runtime.console = value
        }


    private val objJS
        get() = engine.getValue(OBJ_JS) as? Value ?: throw IllegalStateException("$OBJ_JS not found")

    fun eval() {
        engine.execute(rule.code.toScriptSource())
    }

    @Suppress("UNCHECKED_CAST")
    fun evalInfo() {
        eval()
        objJS.apply {
            rule.name = getMember("name")?.asString() ?: ""
            rule.ruleId = getMember("id")?.asString() ?: ""
            rule.author = getMember("author")?.asString() ?: ""

            rule.tags = convertValueToMap(getMember("tags"))

            val tagsDataValue = getMember("tagsData")
            rule.tagsData = if (tagsDataValue != null && tagsDataValue.hasMembers()) {
                convertValueToTagsDataMap(tagsDataValue)
            } else {
                emptyMap()
            }

            runCatching {
                rule.version = getMember("version")?.asInt() ?: 0
            }.onFailure {
                throw NumberFormatException(this@SpeechRuleEngine.context.getString(R.string.plugin_bad_format))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertValueToMap(value: Value?): Map<String, String> {
        if (value == null || !value.hasMembers()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (key in value.memberKeys) {
            result[key] = value.getMember(key).asString()
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertValueToTagsDataMap(value: Value): TagsDataMap {
        val result = mutableMapOf<String, Map<String, Map<String, String>>>()
        for (key1 in value.memberKeys) {
            val innerMap = mutableMapOf<String, Map<String, String>>()
            val value1 = value.getMember(key1)
            if (value1.hasMembers()) {
                for (key2 in value1.memberKeys) {
                    val value2 = value1.getMember(key2)
                    if (value2.hasMembers()) {
                        innerMap[key2] = convertValueToMap(value2)
                    }
                }
            }
            result[key1] = innerMap
        }
        return result
    }

    fun getTagName(tag: String, tagMap: Map<String, String>): String {
        return engine.invokeMethod(objJS, FUNC_GET_TAG_NAME, tag, tagMap)?.toString() ?: ""
    }

    data class TagData(val id: String, val value: String)

    fun handleText(text: String, list: List<SpeechRuleInfo> = emptyList()): List<TextWithTag> {
        val tagsDataMap: MutableMap<String, MutableMap<String, MutableList<Map<String, String>>>> =
            mutableMapOf()
        list.forEach { info ->
            if (tagsDataMap[info.tag] == null)
                tagsDataMap[info.tag] = mutableMapOf()

            info.tagData.forEach {
                if (tagsDataMap[info.tag]!![it.key] == null)
                    tagsDataMap[info.tag]!![it.key] = mutableListOf()

                tagsDataMap[info.tag]!![it.key]!!.add(
                    mapOf(
                        "id" to info.configId.toString(),
                        "value" to it.value
                    )
                )
            }
        }
        return handleText(text, tagsDataMap)
    }

    /** ['dialogue']['role']= List<TagData>
     *@param tagsDataSet 例： key: dialogue, value: map(key: role, value: [{tagDataId:111, 张三, 李四])
     */
    fun handleText(
        text: String,
        tagsDataMap: Map<String, Map<String, List<Map<String, String>>>>
    ): List<TextWithTag> {
        val resultList: MutableList<TextWithTag> = mutableListOf()
        val resultValue = engine.invokeMethod(objJS, FUNC_HANDLE_TEXT, text, tagsDataMap) as? Value
        resultValue?.let {
            if (it.hasArrayElements()) {
                val size = it.arraySize.toInt()
                for (i in 0 until size) {
                    val elem = it.getArrayElement(i.toLong())
                    if (elem.hasMembers()) {
                        resultList.add(
                            TextWithTag(
                                elem.getMember("text")?.asString() ?: "",
                                elem.getMember("tag")?.asString() ?: "",
                                elem.getMember("id")?.asLong() ?: 0L
                            )
                        )
                    }
                }
            }
        }
        return resultList
    }

    @Suppress("UNCHECKED_CAST")
    fun splitText(text: String): List<CharSequence> {
        return engine.invokeMethod(
            objJS,
            FUNC_SPLIT_TEXT,
            text
        ) as List<CharSequence>
    }

    data class TextWithTag(val text: String, val tag: String, val id: Long)
}