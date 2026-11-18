/*
 * Copyright 2023 Reid Byun.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.reidsync.kxjsonpatch

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Applies RFC 6902 operations to an immutable [JsonElement], producing a new document for each operation.
 * Every failure is reported as a [JsonPatchApplicationException]; the input document is never mutated.
 */
internal class JsonPatchEditingContextImpl(override var document: JsonElement) : JsonPatchEditingContext {

    // RFC 6902 section 4.1
    override fun add(path: List<String>, value: JsonElement) {
        document = if (path.isEmpty()) value else update(document, path, 0, "add") { parent, token ->
            when (parent) {
                is JsonObject -> parent.with(token, value)
                is JsonArray -> parent.inserted(JsonPointer.parseIndex(token, parent.size, allowAppend = true, "add", path), value)
                else -> throw JsonPatchApplicationException(
                    "[add Operation] parent is not a container, path: ${JsonPointer.format(path)} | parent: $parent",
                )
            }
        }
    }

    // RFC 6902 section 4.2
    override fun remove(path: List<String>) {
        if (path.isEmpty()) throw JsonPatchApplicationException("[remove Operation] the whole document cannot be removed (empty path)")
        document = update(document, path, 0, "remove") { parent, token ->
            when (parent) {
                is JsonObject -> {
                    if (!parent.containsKey(token)) throw noSuchPath("remove", path)
                    parent.without(token)
                }
                is JsonArray -> parent.removedAt(JsonPointer.parseIndex(token, parent.size, allowAppend = false, "remove", path))
                else -> throw noSuchPath("remove", path)
            }
        }
    }

    // RFC 6902 section 4.3
    override fun replace(path: List<String>, value: JsonElement) {
        document = if (path.isEmpty()) value else update(document, path, 0, "replace") { parent, token ->
            when (parent) {
                is JsonObject -> {
                    if (!parent.containsKey(token)) throw noSuchPath("replace", path)
                    parent.with(token, value)
                }
                is JsonArray -> parent.replacedAt(JsonPointer.parseIndex(token, parent.size, allowAppend = false, "replace", path), value)
                else -> throw noSuchPath("replace", path)
            }
        }
    }

    // RFC 6902 section 4.4
    override fun move(fromPath: List<String>, toPath: List<String>) {
        val value = read(document, fromPath, "move")
        if (fromPath == toPath) return
        if (toPath.size > fromPath.size && toPath.subList(0, fromPath.size) == fromPath) {
            throw JsonPatchApplicationException(
                "[move Operation] 'from' ${JsonPointer.format(fromPath)} must not be a proper prefix of 'path' ${JsonPointer.format(toPath)}",
            )
        }
        remove(fromPath)
        add(toPath, value)
    }

    // RFC 6902 section 4.5
    override fun copy(fromPath: List<String>, toPath: List<String>) {
        val value = read(document, fromPath, "copy")
        add(toPath, value)
    }

    // RFC 6902 section 4.6
    override fun test(path: List<String>, value: JsonElement) {
        val actual = read(document, path, "test")
        if (!actual.jsonEquals(value)) {
            throw JsonPatchApplicationException(
                "[test Operation] value mismatch at ${JsonPointer.format(path)}: expected $value but was $actual",
            )
        }
    }

    /** Resolves [path] against [root]; fails when any segment does not exist. */
    private fun read(root: JsonElement, path: List<String>, op: String): JsonElement {
        var node = root
        for (token in path) {
            node = when (node) {
                is JsonObject -> node[token] ?: throw noSuchPath(op, path)
                is JsonArray -> node[JsonPointer.parseIndex(token, node.size, allowAppend = false, op, path)]
                else -> throw noSuchPath(op, path)
            }
        }
        return node
    }

    /**
     * Walks [path] from [node] down to the parent of the last token, applies [edit] to that parent and
     * rebuilds the containers on the way back up. Missing intermediate segments are an error.
     */
    private fun update(
        node: JsonElement,
        path: List<String>,
        depth: Int,
        op: String,
        edit: (parent: JsonElement, token: String) -> JsonElement,
    ): JsonElement {
        val token = path[depth]
        if (depth == path.size - 1) return edit(node, token)
        return when (node) {
            is JsonObject -> {
                val child = node[token] ?: throw noSuchPath(op, path)
                node.with(token, update(child, path, depth + 1, op, edit))
            }
            is JsonArray -> {
                val index = JsonPointer.parseIndex(token, node.size, allowAppend = false, op, path)
                node.replacedAt(index, update(node[index], path, depth + 1, op, edit))
            }
            else -> throw noSuchPath(op, path)
        }
    }

    private fun noSuchPath(op: String, path: List<String>) =
        JsonPatchApplicationException("[$op Operation] no such path in source, path: ${JsonPointer.format(path)}")
}
