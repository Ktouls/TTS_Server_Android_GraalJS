package com.github.jing332.script.runtime

import com.github.jing332.script.runtime.console.Console
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

open class GraalJSScriptRuntime(
    var environment: Environment,
    var console: Console = Console(),
) {
    companion object {
        val sharedContext: Context by lazy {
            Context.newBuilder("js")
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
        val bindings = context?.getBindings("js") ?: sharedContext.getBindings("js")

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
        bindings.putMember("WebSocket", context.asValue { args: Array<Value> ->
            val url = if (args.isNotEmpty()) args[0].asString() else ""
            val headers = if (args.size > 1 && args[1].hasMembers()) {
                val headersMap = mutableMapOf<CharSequence, CharSequence>()
                for (key in args[1].memberKeys) {
                    headersMap[key] = args[1].getMember(key).asString()
                }
                headersMap
            } else {
                emptyMap()
            }
            GraalJSWebSocket(context, url, headers)
        })
    }

    protected fun getBindings() = context?.getBindings("js") ?: sharedContext.getBindings("js")
}
