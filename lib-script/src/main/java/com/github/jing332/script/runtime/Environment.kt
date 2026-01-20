package com.github.jing332.script.runtime

/**
 * 环境配置数据类
 * 保存插件运行时的环境信息
 */
data class Environment(val cacheDir: String, val id: String) {
    companion object {
        /**
         * 获取当前环境对象
         * 在 GraalJS 中，通过 bindings.getMember("environment") 获取
         */
        @JvmStatic
        lateinit var current: Environment
            private set

        /**
         * 设置当前环境
         */
        @JvmStatic
        fun setEnvironment(env: Environment) {
            current = env
        }

        /**
         * 获取当前环境（非静态方式）
         */
        fun environment(): Environment = current
    }
}