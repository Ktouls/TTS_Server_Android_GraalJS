package com.github.jing332.script.runtime

import com.github.jing332.script.runtime.GlobalWebview.Companion.NAME
import org.graalvm.polyglot.Value

/**
 * GraalJS 版本的全局对象基类
 * 原本继承 ScriptableObject()，现改为普通基类
 * GraalJS 通过 HostAccess.ALL 自动暴露属性和方法
 */
abstract class Global {
    /**
     * 初始化全局对象到 GraalJS 作用域
     * @param bindings GraalJS 绑定对象
     * @param name 注入的全局变量名
     */
    fun init(bindings: Value, name: String) {
        bindings.putMember(name, this)
    }

    override fun toString(): String = "Global[${this::class.simpleName}]"
}