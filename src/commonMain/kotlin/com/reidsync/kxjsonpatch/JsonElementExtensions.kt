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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Public convenience API
 */

/** Applies [patch] (an RFC 6902 document) to this element and returns the result. This element is not modified. */
fun JsonElement.apply(patch: JsonElement): JsonElement = JsonPatch.apply(patch, this)

/** Generates an RFC 6902 patch that transforms this element into [with]. */
fun JsonElement.generatePatch(with: JsonElement): JsonElement = JsonDiff.asJson(this, with)

/*
 * RFC 6902 section 4.6 equality: strings by characters, numbers by numeric value, arrays element-wise,
 * objects member-wise regardless of order, literals by identity.
 */
internal fun JsonElement.jsonEquals(other: JsonElement): Boolean = when {
    this is JsonNull || other is JsonNull -> this is JsonNull && other is JsonNull
    this is JsonPrimitive && other is JsonPrimitive -> primitiveEquals(this, other)
    this is JsonArray && other is JsonArray -> size == other.size && indices.all { this[it].jsonEquals(other[it]) }
    this is JsonObject && other is JsonObject ->
        size == other.size && all { (key, value) -> other[key]?.let { value.jsonEquals(it) } ?: false }
    else -> false
}

private fun primitiveEquals(a: JsonPrimitive, b: JsonPrimitive): Boolean {
    if (a.isString || b.isString) return a.isString && b.isString && a.content == b.content
    if (a.content == b.content) return true // same literal (booleans, or numbers with identical lexical form)
    val la = a.content.toLongOrNull()
    val lb = b.content.toLongOrNull()
    if (la != null && lb != null) return la == lb
    val da = a.content.toDoubleOrNull()
    val db = b.content.toDoubleOrNull()
    return da != null && db != null && da == db
}

/*
 * Copy-on-write helpers for immutable kotlinx JsonElement containers
 */

internal fun JsonObject.with(key: String, value: JsonElement): JsonObject =
    JsonObject(toMutableMap().also { it[key] = value })

internal fun JsonObject.without(key: String): JsonObject =
    JsonObject(toMutableMap().also { it.remove(key) })

internal fun JsonObject.add(key: String, value: JsonElement): JsonObject = with(key, value)

internal fun JsonObject.addProperty(key: String, value: String): JsonObject = with(key, JsonPrimitive(value))

internal fun JsonObject.addProperty(key: String, value: Number): JsonObject = with(key, JsonPrimitive(value))

internal fun JsonArray.add(value: JsonElement): JsonArray = JsonArray(this + value)

internal fun JsonArray.inserted(index: Int, value: JsonElement): JsonArray =
    JsonArray(toMutableList().also { it.add(index, value) })

internal fun JsonArray.replacedAt(index: Int, value: JsonElement): JsonArray =
    JsonArray(toMutableList().also { it[index] = value })

internal fun JsonArray.removedAt(index: Int): JsonArray =
    JsonArray(toMutableList().also { it.removeAt(index) })
