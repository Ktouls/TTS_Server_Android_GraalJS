package com.github.jing332.script.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.charset.Charset

class GraalJSGlobalFileSystem {
    companion object {
        private val logger = KotlinLogging.logger("GraalJSGlobalFileSystem")

        private fun file(path: String, environment: Environment): File {
            var nicePath = path
            if (path.startsWith("./"))
                nicePath = nicePath.replaceFirst("./", environment.cacheDir + "/${environment.id}")
            else if (!path.startsWith("/"))
                nicePath = environment.cacheDir + "/${environment.id}/$path"

            logger.debug { "getFile(${nicePath})" }
            return File(nicePath)
        }

        fun readText(path: String, charset: String = "UTF-8", environment: Environment): String {
            val f = file(path, environment)
            return f.readText(Charset.forName(charset))
        }

        fun readFile(path: String, environment: Environment): ByteArray {
            val f = file(path, environment)
            return f.readBytes()
        }

        fun writeFile(path: String, body: Any, charset: String? = null, environment: Environment) {
            val f = file(path, environment)
            f.parentFile?.mkdirs()
            if (!f.exists()) f.createNewFile()

            when (body) {
                is CharSequence -> f.writeText(
                    body.toString(),
                    Charset.forName(charset ?: "UTF-8")
                )
                is ByteArray -> f.writeBytes(body)
            }
        }

        fun rm(path: String, recursive: Boolean = false, environment: Environment): Boolean {
            val f = file(path, environment)
            return if (f.exists())
                if (recursive) f.deleteRecursively() else f.delete()
            else false
        }

        fun rename(path: String, newPath: String, environment: Environment): Boolean {
            val f = file(path, environment)
            return if (f.exists())
                f.renameTo(file(newPath, environment))
            else false
        }

        fun mkdir(path: String, recursive: Boolean = false, environment: Environment): Boolean {
            val f = file(path, environment)
            return if (recursive) f.mkdirs() else f.mkdir()
        }

        fun copy(path: String, newPath: String, overwrite: Boolean = false, environment: Environment): Boolean {
            val f = file(path, environment)
            return if (f.exists())
                f.copyTo(file(newPath, environment), overwrite)
            else false
        }

        fun exists(path: String, environment: Environment): Boolean {
            val f = file(path, environment)
            return f.exists()
        }

        fun isFile(path: String, environment: Environment): Boolean {
            val f = file(path, environment)
            return f.isFile
        }
    }
}
