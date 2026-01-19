package com.github.jing332.script.simple

import com.github.jing332.script.runtime.GraalJSScriptRuntime
import com.github.jing332.script.simple.ext.JsExtensions
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.runtime.Environment

/**
 * GraalJS 兼容运行时
 * 适配 JsExtensions 作为环境对象，用于简化脚本引擎的使用
 */
class GraalCompatScriptRuntime(
    private val extensions: JsExtensions,
    console: Console = Console()
) : GraalJSScriptRuntime(Environment(extensions.cacheDir, extensions.engineId), console) {
    // 保存 extensions 引用以便访问其方法
    val ext: JsExtensions get() = extensions
}
