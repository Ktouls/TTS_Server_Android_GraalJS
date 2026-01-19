package com.github.jing332.script.runtime

import org.graalvm.polyglot.Value

data class Environment(val cacheDir: String, val id: String) {
    companion object {
        fun Value.environment(): Environment {
            val envValue = getMember("environment")
            return if (envValue.hasHostObject()) {
                envValue.asHostObject<Environment>()
            } else if (envValue.isHostObject) {
                envValue.asHostObject()
            } else {
                throw IllegalStateException("Environment not found")
            }
        }
    }
}