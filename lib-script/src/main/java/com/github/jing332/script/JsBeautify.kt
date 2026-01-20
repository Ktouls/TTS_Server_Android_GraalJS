package com.github.jing332.script

import android.content.Context
import org.graalvm.polyglot.Value

/**
 * GraalJS 版本的 JS 代码美化工具
 * 原本使用 Rhino 的 Context.enter()，现改为 GraalJS
 */
class JsBeautify(context: Context) {
    private var formatJsFunc: Value? = null

    init {
        GraalJsRuntime.create().use { graalContext ->
            val source = context.assets.open("js/beautifier.js").bufferedReader().readText()

            graalContext.eval("js", source)

            // 获取全局的 js_beautify 函数
            val bindings = graalContext.getBindings("js")
            formatJsFunc = bindings.getMember("js_beautify")
        }
    }

    /**
     * 格式化 JavaScript 代码
     */
    fun format(code: String): String {
        val func = formatJsFunc
            ?: throw IllegalStateException("js_beautify function not initialized")

        if (!func.canExecute()) {
            throw IllegalStateException("js_beautify is not a function")
        }

        val result = func.execute(code)
        return result.asString()
    }
}