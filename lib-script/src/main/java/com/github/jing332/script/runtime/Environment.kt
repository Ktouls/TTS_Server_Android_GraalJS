package com.github.jing332.script.runtime

import org.graalvm.polyglot.Value

data class Environment(val cacheDir: String, val id: String) {
    companion object {
        fun Value.environment(): Environment {
            val envValue = getMember("environment")
            return if (envValue.isHostObject) {
                envValue.asHostObject<Environment>()
            } else {
                throw IllegalStateException("Environment not found")
            }
        }
    }
}