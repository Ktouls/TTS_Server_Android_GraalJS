@file:OptIn(ExperimentalUuidApi::class)

package com.github.jing332.script.runtime

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class GraalJSGlobalUUID {
    companion object {
        fun v4(): String {
            return Uuid.random().toString()
        }

        fun stringify(bytes: ByteArray): String {
            return Uuid.fromByteArray(bytes).toString()
        }

        fun parse(str: String): ByteArray {
            return Uuid.parse(str).toByteArray()
        }

        fun validate(str: String): Boolean {
            return try {
                Uuid.parse(str)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
}
