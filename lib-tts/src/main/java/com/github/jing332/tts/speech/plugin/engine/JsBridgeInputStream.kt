package com.github.jing332.tts.speech.plugin.engine

import androidx.annotation.Keep
import com.github.jing332.script.engine.ScriptValueUtils
import com.github.jing332.script.exception.ScriptException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import org.graalvm.polyglot.Value
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class JsBridgeInputStream : InputStream() {
    companion object {
        private const val TAG = "JsBridgeInputStream"
        private val logger = KotlinLogging.logger(TAG)
    }

    private val pis: PipedInputStream = PipedInputStream()
    private val pos: PipedOutputStream = PipedOutputStream(pis)
    private var isClosed = false
    private var errorCause: Exception? = null
    private val hasError: Boolean
        get() = errorCause != null

    private fun checkError() {
        errorCause?.let {
            throw it
        }
    }

    override fun read(): Int {
        checkError()
        if (isClosed && pis.available() == 0) {
            return -1 // Signal end of stream
        }

        try {
            val byte = pis.read() // Reads a single byte
            checkError()
            return byte
        } catch (e: IOException) {
            errorCause = e
            throw e
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkError()
        if (isClosed && pis.available() == 0) {  // Check for EOF *before* blocking read.  Crucial!
            return -1
        }
        if (off < 0 || len < 0 || len > b.size - off) {
            throw IndexOutOfBoundsException()
        } else if (len == 0) {
            return 0
        }

        try {
            val byte = pis.read(b, off, len) // Reads up to 'len' bytes into the buffer
            checkError()
            return byte
        } catch (e: IOException) {
            errorCause = e
            throw e
        }
    }

    override fun available(): Int {
        checkError()
        return pis.available().apply {
            checkError()
        }
    }

    @Synchronized
    override fun close() {
        if (!isClosed) {
            isClosed = true
            try {
                pos.close() // Close output end first!  Very important.
            } finally {
                pis.close() // Then close the input end.
            }
        }
    }

    /**
     *  Interface for JavaScript to interact with the OutputStream.  The names
     *  and signatures MUST match your Kotlin definitions.
     */
    @Keep
    interface Callback {
        fun write(data: Any?)
        fun close()
        fun error(data: Any?)
    }

    suspend fun getCallback(mutex: Mutex): Callback {
        mutex.lock()
        return object : Callback {
            private var length = 0
            private fun writeBytes(data: ByteArray) {
                length += data.size
                logger.debug { "write(${data.size}) byteWritten: $length" }

                if (isClosed || hasError) return

                try {
                    pos.write(data)
                    pos.flush()
                } catch (e: IOException) {
                    errorCause = e
                    try {
                        close()
                    } catch (ignored: IOException) {
                    }
                }
            }

            override fun write(data: Any?) {
                when (data) {
                    is ByteArray -> writeBytes(data)
                    is String -> writeBytes(data.toByteArray())
                    is Value -> {
                        val javaObj = ScriptValueUtils.toJavaType(data)
                        when (javaObj) {
                            is ByteArray -> writeBytes(javaObj)
                            is IntArray -> writeBytes(ByteArray(javaObj.size * 4).apply {
                                for (i in javaObj.indices) {
                                    set(i * 4, (javaObj[i] shr 24).toByte())
                                    set(i * 4 + 1, (javaObj[i] shr 16).toByte())
                                    set(i * 4 + 2, (javaObj[i] shr 8).toByte())
                                    set(i * 4 + 3, javaObj[i].toByte())
                                }
                            })
                            else -> throw IllegalArgumentException("Unsupported JavaScript type for audio: ${javaObj.javaClass.name}")
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported data type: ${data?.javaClass?.name ?: "null"}")
                }
            }

            override fun close() {
                logger.debug { "close" }

                try {
                    if (length <= 0) errorCause = IOException("No data written")

                    this@JsBridgeInputStream.close()
                } catch (e: IOException) {
                    errorCause = e
                } finally {
                    if (mutex.isLocked) mutex.unlock()
                }
            }

            override fun error(data: Any?) {
                logger.debug { "error(${data})" }

                errorCause = ScriptException(
                    sourceName = "",
                    lineNumber = 0,
                    columnNumber = 0,
                    message = data?.toString() ?: "Unknown error",
                    cause = null
                )
                try {
                    close()
                } catch (ignored: IOException) {
                }
            }
        }
    }
}
