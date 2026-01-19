package com.github.jing332.script.runtime

import com.github.jing332.common.LogLevel
import com.github.jing332.script.runtime.console.Console
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable

class GraalJSConsole(private val console: Console) {
    fun log(vararg args: Any?) {
        println(args, LogLevel.INFO)
    }

    fun debug(vararg args: Any?) {
        println(args, LogLevel.DEBUG)
    }

    fun info(vararg args: Any?) {
        println(args, LogLevel.INFO)
    }

    fun warn(vararg args: Any?) {
        println(args, LogLevel.WARN)
    }

    fun error(vararg args: Any?) {
        println(args, LogLevel.ERROR)
    }

    private fun println(args: Array<out Any?>, level: Int) {
        val str = args.joinToString(" ") { it?.toString() ?: "null" }
        console.write(level, str)
    }
}
