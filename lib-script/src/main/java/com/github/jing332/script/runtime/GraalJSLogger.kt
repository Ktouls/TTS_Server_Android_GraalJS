package com.github.jing332.script.runtime

import com.github.jing332.common.LogLevel
import com.github.jing332.script.runtime.console.Console
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable

class GraalJSLogger(private val console: Console) {
    fun log(vararg args: Any?) {
        println(args, LogLevel.INFO)
    }

    fun t(vararg args: Any?) {
        println(args, LogLevel.TRACE)
    }

    fun d(vararg args: Any?) {
        println(args, LogLevel.DEBUG)
    }

    fun i(vararg args: Any?) {
        println(args, LogLevel.INFO)
    }

    fun w(vararg args: Any?) {
        println(args, LogLevel.WARN)
    }

    fun e(vararg args: Any?) {
        println(args, LogLevel.ERROR)
    }

    private fun println(args: Array<out Any?>, level: Int) {
        val str = args.joinToString(" ") { it?.toString() ?: "null" }
        console.write(level, str)
    }

    fun getLogFunction(methodName: String): ProxyExecutable {
        return ProxyExecutable { args ->
            val str = args.joinToString(" ") { it?.toString() ?: "null" }
            console.write(LogLevel.INFO, str)
            null
        }
    }
}
