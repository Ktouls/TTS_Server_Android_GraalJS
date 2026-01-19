package com.github.jing332.script.runtime

import com.github.jing332.script.runtime.console.Console
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

open class GraalJSScriptRuntime(
    var environment: Environment,
    var console: Console = Console(),
) {
    companion object {
        init {
            // Android 兼容性：禁用 GraalVM 的模块系统相关功能
            System.setProperty("polyglot.engine.WarnInterpreterOnly", "false")
            System.setProperty("polyglot", "true")
        }

        // 禁用 sharedContext，因为 GraalVM 23.1.2 在 Android 上有兼容性问题
        // 需要时直接创建新的 Context
        @JvmStatic
        @Suppress("UNUSED")
        fun createSharedContext(): Context {
            return Context.newBuilder("js")
                .allowAllAccess(false)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup { className ->
                    !className.startsWith("java.lang.reflect.") &&
                    !className.startsWith("dalvik.system.") &&
                    !className.contains("android.app.ActivityThread")
                }
                .allowHostClassLoading(true)
                .build()
        }
    }

    private var context: Context? = null

    fun createContext(): Context {
        val ctx = Context.newBuilder("js")
            .allowAllAccess(false)
            .allowHostAccess(HostAccess.newBuilder(HostAccess.ALL)
                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true)
                .build())
            .allowHostClassLookup { className ->
                !className.startsWith("java.lang.reflect.") &&
                !className.startsWith("dalvik.system.") &&
                !className.contains("android.app.ActivityThread")
            }
            .allowHostClassLoading(true)
            .build()

        context = ctx
        return ctx
    }

    fun init() {
        val ctx = context ?: createContext()
        val bindings = ctx.getBindings("js")

        // Put environment
        bindings.putMember("environment", environment)

        // Initialize console
        val consoleObj = GraalJSConsole(console)
        bindings.putMember("console", consoleObj)

        // Initialize logger
        val loggerObj = GraalJSLogger(console)
        bindings.putMember("logger", loggerObj)
        bindings.putMember("println", loggerObj.getLogFunction("log"))

        // Initialize http
        bindings.putMember("http", GraalJSGlobalHttp)

        // Initialize fs
        val fsObj = GraalJSGlobalFileSystem
        bindings.putMember("fs", org.graalvm.polyglot.proxy.ProxyObject.fromMap(mapOf(
            "readText" to { path: String, charset: String? -> fsObj.readText(path, charset ?: "UTF-8", environment) },
            "readFile" to { path: String -> fsObj.readFile(path, environment) },
            "writeFile" to { path: String, body: Any, charset: String? -> fsObj.writeFile(path, body, charset, environment) },
            "rm" to { path: String, recursive: Boolean? -> fsObj.rm(path, recursive == true, environment) },
            "rename" to { path: String, newPath: String -> fsObj.rename(path, newPath, environment) },
            "mkdir" to { path: String, recursive: Boolean? -> fsObj.mkdir(path, recursive == true, environment) },
            "copy" to { path: String, newPath: String, overwrite: Boolean? -> fsObj.copy(path, newPath, overwrite == true, environment) },
            "exists" to { path: String -> fsObj.exists(path, environment) },
            "isFile" to { path: String -> fsObj.isFile(path, environment) }
        )))

        // Initialize uuid
        bindings.putMember("uuid", GraalJSGlobalUUID)

        // Initialize webview
        bindings.putMember("webview", GraalJSGlobalWebview)

        // Initialize Buffer
        bindings.putMember("Buffer", GraalJSBuffer)

        // Initialize WebSocket constructor
        bindings.putMember("WebSocket", ctx.asValue { args: Array<Value> ->
            val url = if (args.isNotEmpty()) args[0].asString() else ""
            val headers = if (args.size > 1 && args[1].hasMembers()) {
                val headersMap = mutableMapOf<CharSequence, CharSequence>()
                val memberKeys = args[1].memberKeys
                for (key in memberKeys) {
                    headersMap[key] = args[1].getMember(key).asString()
                }
                headersMap
            } else {
                emptyMap()
            }
            GraalJSWebSocket(ctx, url, headers)
        })
    }

    protected fun getBindings() = (context ?: createContext()).getBindings("js")
}
