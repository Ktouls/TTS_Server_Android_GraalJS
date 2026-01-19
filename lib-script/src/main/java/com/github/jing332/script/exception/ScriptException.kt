package com.github.jing332.script.exception

import org.graalvm.polyglot.PolyglotException

fun <R> runScriptCatching(block: () -> R): R {
    return try {
        block()
    } catch (e: Throwable) {
        throw ScriptException.from(e)
    }
}

open class ScriptException(
    val sourceName: String = "",
    val lineNumber: Int = 0,
    val columnNumber: Int = 0,

    override val message: String? = "",
    override val cause: Throwable? = null,
) : RuntimeException() {
    companion object {
        fun from(throwable: Throwable): ScriptException {
            val (sourceName, lineNumber, columnNumber, errorMsg) = when (throwable) {
                is PolyglotException -> {
                    val sourceLocation = throwable.sourceLocation
                    val line = sourceLocation?.startLine ?: -1
                    val column = sourceLocation?.startColumn ?: -1
                    val source = sourceLocation?.source?.name ?: ""
                    Triple(source, line, column) to throwable.message ?: throwable.toString()
                }
                else -> {
                    Triple("", -1, -1) to (throwable.message ?: throwable.toString())
                }
            }

            return ScriptException(
                sourceName,
                lineNumber,
                columnNumber,
                errorMsg,
                throwable
            )
        }
    }
}
