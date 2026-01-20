package com.github.jing332.script.simple

import android.content.Context
import com.github.jing332.script.engine.GraalScriptEngine
import com.github.jing332.script.source.ScriptSource

class SimpleScriptEngine(context: Context, id: String) :
    GraalScriptEngine(context) {

    override fun init() {
        // No special initialization needed
    }

    override fun execute(source: ScriptSource): Any? {
        return super.execute(source)
    }
}