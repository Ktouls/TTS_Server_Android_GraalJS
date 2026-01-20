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
    var polyglotStackTrace: String? = null

    override fun toString(): String {
        return super.toString() + (polyglotStackTrace?.let { "\n$it" } ?: "")
    }

    companion object {
        fun from(throwable: Throwable): ScriptException {
            return when (throwable) {
                is PolyglotException -> {
                    ScriptException(
                        sourceName = throwable.sourceLocation?.sourceName ?: "",
                        lineNumber = throwable.sourceLocation?.lineNumber ?: 0,
                        columnNumber = throwable.sourceLocation?.columnNumber ?: 0,
                        message = throwable.message,
                        cause = throwable
                    ).apply {
                        polyglotStackTrace = throwable.polyglotStackTrace.toString()
                    }
                }
                else -> ScriptException(
                    sourceName = "",
                    lineNumber = 0,
                    columnNumber = 0,
                    message = throwable.message,
                    cause = throwable
                )
            }
        }
    }
}