package com.github.jing332.script.runtime

import com.github.jing332.script.engine.GraalJsRuntime
import com.github.jing332.script.exception.runScriptCatching
import com.github.jing332.script.runtime.Environment.Companion.environment
import io.github.oshai.kotlinlogging.KotlinLogging
import org.graalvm.polyglot.Value
import java.io.File
import java.nio.charset.Charset

/**
 * GraalJS 版本的文件系统对象
 * 原本继承 Global -> ScriptableObject，现改为纯 Kotlin 类
 * GraalJS 通过 HostAccess.ALL 自动暴露方法
 */
class GlobalFileSystem {
    companion object {
        private val logger = KotlinLogging.logger("NativeFileSystem")
        const val NAME = "fs"

        /**
         * 初始化文件系统对象到 GraalJS 作用域
         */
        @JvmStatic
        fun init(bindings: Value, sealed: Boolean) {
            bindings.putMember(NAME, GlobalFileSystem())
        }

        /**
         * 获取文件对象（处理相对路径）
         */
        private fun getFile(path: String): File {
            val env = environment()
            var nicePath = path
            if (path.startsWith("./"))
                nicePath = nicePath.replaceFirst("./", env.cacheDir + "/${env.id}")
            else if (!path.startsWith("/"))
                nicePath = env.cacheDir + "/${env.id}/$path"

            logger.debug { "getFile(${nicePath})" }
            return File(nicePath)
        }
    }

    /**
     * 读取文本文件
     */
    fun readText(path: Any, charset: Any? = null): String = runScriptCatching {
        val pathStr = path.toString()
        val charsetName = charset?.toString() ?: "UTF-8"
        val file = getFile(pathStr)
        file.readText(Charset.forName(charsetName))
    }

    /**
     * 读取二进制文件
     */
    fun readFile(path: Any): ByteArray = runScriptCatching {
        val pathStr = path.toString()
        val file = getFile(pathStr)
        file.readBytes()
    }

    /**
     * 写入文件
     * 支持字符串或字节数组
     */
    fun writeFile(path: Any, body: Any, charset: Any? = null): Any = runScriptCatching {
        val pathStr = path.toString()
        val file = getFile(pathStr)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()

        when (body) {
            is CharSequence -> file.writeText(
                body.toString(),
                Charset.forName(charset?.toString() ?: "UTF-8")
            )
            is ByteArray -> file.writeBytes(body)
            is Value -> {
                // 处理 GraalJS Value 类型
                val javaObj = GraalJsRuntime.convertValue(body)
                when (javaObj) {
                    is ByteArray -> file.writeBytes(javaObj)
                    is String -> file.writeText(javaObj)
                    else -> throw IllegalArgumentException("Unsupported body type")
                }
            }
            else -> throw IllegalArgumentException("Unsupported body type: ${body.javaClass.name}")
        }

        Unit
    }

    /**
     * 删除文件或目录
     */
    fun rm(path: Any, recursive: Any? = null): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val isRecursive = recursive == true || recursive === null
        val file = getFile(pathStr)
        if (file.exists()) {
            if (isRecursive) file.deleteRecursively()
            else file.delete()
        } else {
            false
        }
    }

    /**
     * 重命名文件或目录
     */
    fun rename(path: Any, newPath: Any): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val newPathStr = newPath.toString()
        val file = getFile(pathStr)
        if (file.exists()) {
            file.renameTo(getFile(newPathStr))
        } else {
            false
        }
    }

    /**
     * 创建目录
     */
    fun mkdir(path: Any, recursive: Any? = null): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val isRecursive = recursive == true
        val file = getFile(pathStr)

        if (isRecursive) file.mkdirs()
        else file.mkdir()
    }

    /**
     * 复制文件或目录
     */
    fun copy(path: Any, newPath: Any, overwrite: Any? = null): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val newPathStr = newPath.toString()
        val isOverwrite = overwrite == true
        val file = getFile(pathStr)

        if (file.exists()) {
            file.copyTo(getFile(newPathStr), isOverwrite)
        } else {
            false
        }
    }

    /**
     * 检查文件或目录是否存在
     */
    fun exists(path: Any): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val file = getFile(pathStr)
        file.exists()
    }

    /**
     * 检查是否为文件
     */
    fun isFile(path: Any): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val file = getFile(pathStr)
        file.isFile
    }

    /**
     * 检查是否为目录
     */
    fun isDirectory(path: Any): Boolean = runScriptCatching {
        val pathStr = path.toString()
        val file = getFile(pathStr)
        file.isDirectory
    }
}