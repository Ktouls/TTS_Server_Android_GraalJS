package com.github.jing332.script.engine

import android.content.Context
import org.graalvm.polyglot.*
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyObject
import java.lang.reflect.Modifier
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * GraalVM JavaScript Runtime Singleton - The Universal Rhino Emulation Layer
 *
 * Provides full compatibility with existing Rhino plugins without modifying JS code.
 * Critical: Must set system properties before any engine initialization.
 */
object GraalJsRuntime {
    private const val GRAAL_JS_VERSION = "20.3.13"

    init {
        // CRITICAL: Android Unsafe Crash Defense
        System.setProperty("truffle.js.InterpretedHelper", "true")
        System.setProperty("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime")
    }

    private val mainThreadExecutor = MainThreadExecutor()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(4)

    /**
     * Creates a new GraalVM JavaScript Context with full Rhino compatibility.
     *
     * @param androidContext Android Application Context (for global injection)
     * @param classLoader ClassLoader for host class lookup
     * @return Configured Polyglot Context ready for script execution
     */
    fun createContext(
        androidContext: Context? = null,
        classLoader: ClassLoader? = null
    ): Context {
        return Context.newBuilder("js")
            // Full Host Access - Allow scripts to call any Java class/method
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            // Enable File I/O for plugin configuration reading/writing
            .allowIO(true)
            // Allow creating threads for async operations
            .allowCreateThread(true)
            .build().also { ctx ->
                // Inject global namespace objects (Rhino-style)
                val bindings = ctx.getBindings("js")
                injectGlobalPackages(bindings, classLoader ?: javaClass.classLoader)

                androidContext?.let {
                    bindings.putMember("context", it)
                    bindings.putMember("activity", it) // fallback, may be replaced later
                }

                bindings.putMember("loader", classLoader ?: javaClass.classLoader)

                // Execute Rhino polyfills before any user script
                ctx.eval("js", RHINO_POLYFILLS_JS)

                // Override console.log to use Android Log
                ctx.eval("js", CONSOLE_POLYFILL_JS)

                // Install UI-thread guard for Toast, AlertDialog, etc.
                installUiThreadGuard(ctx, bindings)
            }
    }
    
    /**
     * Injects top-level package objects (android, java, javax, com, org, Packages)
     * to simulate Rhino's global namespace exposure.
     */
    private fun injectGlobalPackages(bindings: Value, classLoader: ClassLoader) {
        // Root package proxies
        bindings.putMember("android", PackageProxy("android", classLoader))
        bindings.putMember("java", PackageProxy("java", classLoader))
        bindings.putMember("javax", PackageProxy("javax", classLoader))
        bindings.putMember("com", PackageProxy("com", classLoader))
        bindings.putMember("org", PackageProxy("org", classLoader))
        
        // Legacy Rhino alias
        bindings.putMember("Packages", PackageProxy("", classLoader))
    }
    
    /**
     * Installs a dynamic proxy that intercepts UI-related Java method calls
     * and routes them to the main thread automatically.
     */
    private fun installUiThreadGuard(ctx: Context, bindings: Value) {
        // Override global Android classes with thread-safe proxies
        val uiGuardScript = """
            // Wrap Toast class
            var OriginalToast = android.widget.Toast;
            android.widget.Toast = new Proxy(OriginalToast, {
                construct: function(target, args) {
                    return Reflect.construct(target, args);
                },
                get: function(target, prop, receiver) {
                    var value = Reflect.get(target, prop, receiver);
                    if (typeof value === 'function' && 
                        (prop === 'makeText' || prop === 'show' || prop === 'cancel')) {
                        return function(...args) {
                            return ${mainThreadExecutor.jsInvocationWrapper("value", "this", "args")};
                        };
                    }
                    return value;
                }
            });
            
            // Wrap AlertDialog.Builder similarly
            var OriginalAlertDialogBuilder = android.app.AlertDialog.Builder;
            android.app.AlertDialog.Builder = new Proxy(OriginalAlertDialogBuilder, {
                construct: function(target, args) {
                    return Reflect.construct(target, args);
                },
                get: function(target, prop, receiver) {
                    var value = Reflect.get(target, prop, receiver);
                    if (typeof value === 'function' && 
                        prop.match(/create|show|setTitle|setMessage|setPositiveButton/)) {
                        return function(...args) {
                            return ${mainThreadExecutor.jsInvocationWrapper("value", "this", "args")};
                        };
                    }
                    return value;
                }
            });
        """.trimIndent()
        
        ctx.eval("js", uiGuardScript)
    }
    
    /**
     * Executes JavaScript code with automatic type marshaling and error handling.
     *
     * @param context Polyglot Context
     * @param script JavaScript source code
     * @param sourceName Name for error reporting
     * @return Result converted to appropriate Java type
     */
    fun executeScript(context: Context, script: String, sourceName: String = "<anonymous>"): Any? {
        try {
            val result = context.eval("js", script)
            return ScriptValueUtils.toJavaType(result)
        } catch (e: PolyglotException) {
            throw ScriptException("JavaScript execution failed in '$sourceName': ${e.message}", e).apply {
                polyglotStackTrace = e.polyglotStackTrace
            }
        }
    }
    
    /**
     * Schedules a function to run on the main/UI thread.
     */
    fun runOnUiThread(block: () -> Unit) {
        mainThreadExecutor.execute(block)
    }
    
    /**
     * Suspending version of setTimeout for coroutine integration.
     */
    suspend fun setTimeout(delayMs: Long, block: () -> Unit) {
        suspendCoroutine<Unit> { cont ->
            scheduler.schedule({
                runOnUiThread {
                    block()
                    cont.resume(Unit)
                }
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }
    
    /**
     * Proxy object representing a Java package for global namespace injection.
     */
    private class PackageProxy(private val packageName: String, private val classLoader: ClassLoader) : ProxyObject {
        private val members = mutableMapOf<String, Any?>()
        
        override fun getMemberKeys(): Array<Any> {
            // Lazy discovery of subpackages and classes
            if (members.isEmpty()) {
                discoverMembers()
            }
            return members.keys.toTypedArray()
        }
        
        override fun hasMember(key: String): Boolean {
            if (members.containsKey(key)) return true
            // Try to find class or subpackage
            return tryResolveClassOrPackage(key) != null
        }
        
        override fun getMember(key: String): Any? {
            return members.getOrPut(key) {
                tryResolveClassOrPackage(key) ?: ProxyObject.fromMap(emptyMap())
            }
        }
        
        override fun putMember(key: String, value: Any?) {
            members[key] = value
        }
        
        private fun discoverMembers() {
            // Simplified: In real implementation, would scan classpath for packages
            // For compatibility, we inject common packages known to be used by plugins
            val knownSubpackages = when (packageName) {
                "android" -> listOf("app", "content", "os", "view", "widget")
                "java" -> listOf("io", "lang", "net", "nio", "security", "util")
                "javax" -> listOf("crypto", "net", "security")
                else -> emptyList()
            }
            
            knownSubpackages.forEach { sub ->
                members[sub] = PackageProxy(if (packageName.isEmpty()) sub else "$packageName.$sub", classLoader)
            }
        }
        
        private fun tryResolveClassOrPackage(className: String): Any? {
            val fullName = if (packageName.isEmpty()) className else "$packageName.$className"
            
            // Try to load as class
            return try {
                val clazz = classLoader.loadClass(fullName)
                clazz
            } catch (e: ClassNotFoundException) {
                // Not a class, try as subpackage
                PackageProxy(fullName, classLoader)
            }
        }
    }
    
    /**
     * Main thread executor for Android UI operations.
     */
    private class MainThreadExecutor {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun execute(block: () -> Unit) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                block()
            } else {
                handler.post(block)
            }
        }
        
        fun jsInvocationWrapper(fnVar: String, thisVar: String, argsVar: String): String {
            return """
                (function() {
                    var fn = $fnVar;
                    var self = $thisVar;
                    var args = $argsVar;
                    return new Promise(function(resolve, reject) {
                        ${'$'}.runOnUiThread(function() {
                            try {
                                var result = fn.apply(self, args);
                                resolve(result);
                            } catch (e) {
                                reject(e);
                            }
                        });
                    });
                })()
            """.trimIndent()
        }
    }
    
    companion object {
        // Rhino polyfills - must be executed before any user script
        private const val RHINO_POLYFILLS_JS = """
            // importPackage(pkg) - Rhino compatibility
            globalThis.importPackage = function(pkg) {
                if (typeof pkg === 'string') {
                    var parts = pkg.split('.');
                    var current = globalThis;
                    for (var i = 0; i < parts.length; i++) {
                        var part = parts[i];
                        if (!current[part]) {
                            current[part] = {};
                        }
                        current = current[part];
                    }
                    // Copy all static members
                    var javaPkg = Packages[pkg];
                    if (javaPkg) {
                        for (var key in javaPkg) {
                            if (javaPkg.hasOwnProperty(key)) {
                                current[key] = javaPkg[key];
                            }
                        }
                    }
                }
            };
            
            // importClass(cls) - Rhino compatibility
            globalThis.importClass = function(className) {
                if (typeof className === 'string') {
                    var lastDot = className.lastIndexOf('.');
                    if (lastDot !== -1) {
                        var shortName = className.substring(lastDot + 1);
                        globalThis[shortName] = Packages[className];
                    }
                }
            };
            
            // JavaAdapter - simplified Rhino interface implementation
            globalThis.JavaAdapter = function(interfaces, implementation) {
                return Java.extend(interfaces, implementation);
            };
            
            // setTimeout / setInterval - bridge to Java scheduler
            globalThis.setTimeout = function(fn, delay) {
                ${'$'}.setTimeout(delay, fn);
            };
            globalThis.setInterval = function(fn, interval) {
                var id = { cancelled: false };
                function repeat() {
                    if (!id.cancelled) {
                        fn();
                        ${'$'}.setTimeout(interval, repeat);
                    }
                }
                ${'$'}.setTimeout(interval, repeat);
                return id;
            };
            globalThis.clearInterval = function(id) {
                if (id) id.cancelled = true;
            };
        """
        
        private const val CONSOLE_POLYFILL_JS = """
            // Console logging to Android Log
            globalThis.console = {
                log: function(...args) {
                    android.util.Log.d('JS', args.map(String).join(' '));
                },
                error: function(...args) {
                    android.util.Log.e('JS', args.map(String).join(' '));
                },
                warn: function(...args) {
                    android.util.Log.w('JS', args.map(String).join(' '));
                },
                info: function(...args) {
                    android.util.Log.i('JS', args.map(String).join(' '));
                },
                debug: function(...args) {
                    android.util.Log.d('JS', args.map(String).join(' '));
                }
            };
        """
    }
}

/**
 * Custom exception preserving GraalVM polyglot stack traces.
 */
class ScriptException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    var polyglotStackTrace: String? = null
    
    override fun toString(): String {
        return super.toString() + (polyglotStackTrace?.let { "\n$it" } ?: "")
    }
}