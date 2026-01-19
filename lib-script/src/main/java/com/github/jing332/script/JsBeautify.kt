package com.github.jing332.script

import android.content.Context as AndroidContext
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.io.InputStreamReader

class JsBeautify(context: AndroidContext) {
    private val graalContext: Context
    private var formatJsFunc: Value

    init {
        graalContext = org.graalvm.polyglot.Context.newBuilder("js")
            .allowAllAccess(false)
            .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
            .build()

        val source = org.graalvm.polyglot.Source.newBuilder(
            "js",
            InputStreamReader(context.assets.open("js/beautifier.js"), Charsets.UTF_8),
            "beautifier.js"
        ).build()

        graalContext.eval(source)
        formatJsFunc = graalContext.getBindings("js").getMember("js_beautify")
    }

    fun format(code: String): String {
        val result = formatJsFunc.execute(code)
        return result.asString()
    }

    fun close() {
        graalContext.close()
    }
}
