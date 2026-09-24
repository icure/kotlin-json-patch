package com.reidsync.kxjsonpatch

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal enum class NodeType {
    ARRAY,
    OBJECT,
    PRIMITIVE_OR_NULL;

    companion object {
        fun of(node: JsonElement): NodeType = when (node) {
            is JsonArray -> ARRAY
            is JsonObject -> OBJECT
            else -> PRIMITIVE_OR_NULL
        }
    }
}
