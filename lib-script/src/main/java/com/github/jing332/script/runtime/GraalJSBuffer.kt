package com.github.jing332.script.runtime

import cn.hutool.core.util.HexUtil
import com.github.jing332.script.exception.runScriptCatching
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import kotlin.experimental.and

class GraalJSBuffer private constructor(private val data: ByteArray) {
    val length: Int
        get() = data.size

    companion object {
        fun from(input: Any, encoding: String? = null): GraalJSBuffer {
            return when (input) {
                is ByteArray -> GraalJSBuffer(input)
                is GraalJSBuffer -> input
                is String -> {
                    val enc = encoding?.lowercase(Locale.getDefault()) ?: "utf-8"
                    val bytes = when (enc) {
                        "utf8", "utf-8" -> input.toByteArray(StandardCharsets.UTF_8)
                        "base64" -> Base64.getDecoder().decode(input)
                        "hex" -> HexUtil.decodeHex(input)
                        "ascii" -> input.toByteArray(StandardCharsets.US_ASCII)
                        else -> {
                            runCatching { input.toByteArray(Charset.forName(enc)) }
                                .getOrElse { throw IllegalArgumentException("Unsupported encoding: $enc") }
                        }
                    }
                    GraalJSBuffer(bytes)
                }
                else -> throw IllegalArgumentException("First argument must be a string or buffer")
            }
        }

        fun alloc(size: Int): GraalJSBuffer {
            return GraalJSBuffer(ByteArray(size))
        }

        fun fromByteArray(arr: ByteArray): GraalJSBuffer {
            return GraalJSBuffer(arr)
        }
    }

    operator fun get(index: Int): Int {
        return data[index].toInt() and 0xFF
    }

    operator fun set(index: Int, value: Int) {
        data[index] = value.toByte()
    }

    override fun toString(): String {
        return data.toString(StandardCharsets.UTF_8)
    }

    fun toString(encoding: String?): String {
        val enc = encoding?.lowercase(Locale.getDefault()) ?: "utf-8"
        return when (enc) {
            "base64" -> Base64.getEncoder().encodeToString(data)
            "hex" -> data.joinToString("") { "%02x".format(it) }
            "utf8", "utf-8" -> data.toString(StandardCharsets.UTF_8)
            "ascii" -> data.toString(StandardCharsets.US_ASCII)
            else -> {
                runCatching { data.toString(Charset.forName(enc)) }
                    .getOrElse { throw IllegalArgumentException("Unsupported encoding: $enc") }
            }
        }
    }

    fun toByteArray(): ByteArray {
        return data.copyOf()
    }

    fun slice(start: Int, end: Int = data.size): GraalJSBuffer {
        val actualStart = if (start < 0) data.size + start else start
        val actualEnd = if (end < 0) data.size + end else end
        return GraalJSBuffer(data.copyOfRange(actualStart, actualEnd))
    }

    fun copy(target: GraalJSBuffer, targetStart: Int = 0, sourceStart: Int = 0, sourceEnd: Int = data.size): Int {
        val actualSourceStart = if (sourceStart < 0) data.size + sourceStart else sourceStart
        val actualSourceEnd = if (sourceEnd < 0) data.size + sourceEnd else sourceEnd
        val length = actualSourceEnd - actualSourceStart
        System.arraycopy(data, actualSourceStart, target.data, targetStart, length)
        return length
    }
}
