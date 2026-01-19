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
            val sourceName: String
            val lineNumber: Int
            val columnNumber: Int
            val errorMsg: String

            when (throwable) {
                is PolyglotException -> {
                    val sourceLocation = throwable.sourceLocation
                    lineNumber = sourceLocation?.startLine ?: -1
                    columnNumber = sourceLocation?.startColumn ?: -1
                    sourceName = sourceLocation?.source?.name ?: ""
                    errorMsg = throwable.message ?: throwable.toString()
                }
                else -> {
                    sourceName = ""
                    lineNumber = -1
                    columnNumber = -1
                    errorMsg = throwable.message ?: throwable.toString()
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
