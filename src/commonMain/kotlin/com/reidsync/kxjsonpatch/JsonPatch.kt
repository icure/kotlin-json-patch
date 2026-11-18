/*
 * Copyright 2016 flipkart.com zjsonpatch.
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
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * RFC 6902 JSON Patch: validation and application of patch documents.
 *
 * Structural problems with the patch document itself are reported as [InvalidJsonPatchException];
 * problems applying an operation to the target document as [JsonPatchApplicationException].
 * The source document is never mutated; on failure it is left untouched (RFC 6902 section 5).
 */
object JsonPatch {
    private fun requireMember(operation: JsonObject, member: String): JsonElement =
        operation[member] ?: throw InvalidJsonPatchException("Invalid JSON Patch payload (missing '$member' field)")

    private fun value(operation: JsonObject, flags: Set<CompatibilityFlags>): JsonElement =
        operation[PatchMember.VALUE]
            ?: if (CompatibilityFlags.MISSING_VALUES_AS_NULLS in flags) JsonNull
            else throw InvalidJsonPatchException("Invalid JSON Patch payload (missing '${PatchMember.VALUE}' field)")

    private fun operationName(element: JsonElement): String =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidJsonPatchException("Invalid JSON Patch payload ('${PatchMember.OP}' must be a string, was $element)")

    @Throws(InvalidJsonPatchException::class)
    private fun process(patch: JsonElement, processor: JsonPatchApplyProcessor, flags: Set<CompatibilityFlags>) {
        if (patch !is JsonArray) throw InvalidJsonPatchException("Invalid JSON Patch payload (not an array)")
        for (element in patch) {
            val operation = element as? JsonObject
                ?: throw InvalidJsonPatchException("Invalid JSON Patch payload (operation is not an object: $element)")
            val type = Operation.fromName(operationName(requireMember(operation, PatchMember.OP)))
            val path = JsonPointer.parse(requireMember(operation, PatchMember.PATH), PatchMember.PATH)

            when (type) {
                Operation.REMOVE -> processor.edit { remove(path) }
                Operation.ADD -> {
                    val value = value(operation, flags)
                    processor.edit { add(path, value) }
                }
                Operation.REPLACE -> {
                    val value = value(operation, flags)
                    processor.edit { replace(path, value) }
                }
                Operation.MOVE -> {
                    val fromPath = JsonPointer.parse(requireMember(operation, PatchMember.FROM), PatchMember.FROM)
                    processor.edit { move(fromPath, path) }
                }
                Operation.COPY -> {
                    val fromPath = JsonPointer.parse(requireMember(operation, PatchMember.FROM), PatchMember.FROM)
                    processor.edit { copy(fromPath, path) }
                }
                Operation.TEST -> {
                    val value = value(operation, flags)
                    processor.edit { test(path, value) }
                }
            }
        }
    }

    /** Checks that [patch] is a well-formed RFC 6902 document without applying it. */
    @Throws(InvalidJsonPatchException::class)
    @JvmStatic
    @JvmOverloads
    fun validate(patch: JsonElement, flags: Set<CompatibilityFlags> = CompatibilityFlags.defaults()) {
        process(patch, NoopProcessor.INSTANCE, flags)
    }

    /** Applies [patch] to [source] and returns the resulting document. [source] is not modified. */
    @Throws(JsonPatchApplicationException::class)
    @JvmStatic
    @JvmOverloads
    fun apply(patch: JsonElement, source: JsonElement, flags: Set<CompatibilityFlags> = CompatibilityFlags.defaults()): JsonElement {
        val processor = ApplyProcessor(source)
        process(patch, processor, flags)
        return processor.result()
    }
}
