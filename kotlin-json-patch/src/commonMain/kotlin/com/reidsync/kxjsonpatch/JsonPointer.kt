/*
 * Copyright 2026 iCure.
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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 6901 JSON Pointer support.
 *
 * A pointer is represented as the list of its decoded reference tokens; the empty list is the whole document.
 */
internal object JsonPointer {

    /** Parses the `path` / `from` member of a patch operation. */
    fun parse(element: JsonElement, member: String): List<String> {
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidJsonPatchException("Invalid JSON Patch payload ('$member' must be a JSON Pointer string, was $element)")
        return parse(text, member)
    }

    fun parse(text: String, member: String = "path"): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text[0] != '/') {
            throw InvalidJsonPatchException("Invalid JSON Pointer in '$member': \"$text\" must be empty or start with '/'")
        }
        return text.substring(1).split('/').map { decodeToken(it, text, member) }
    }

    /** Formats decoded tokens back into RFC 6901 string form. */
    fun format(tokens: List<String>): String =
        tokens.joinToString("") { "/" + it.replace("~", "~0").replace("/", "~1") }

    /**
     * Interprets [token] as an array index (RFC 6901 section 4: digits without leading zeros, or `-`).
     *
     * `-` designates the position after the last element; it is only meaningful for `add`, where [allowAppend]
     * is true. Valid indices are `0..size-1`, or `0..size` when [allowAppend] is true.
     */
    fun parseIndex(token: String, size: Int, allowAppend: Boolean, op: String, path: List<String>): Int {
        if (token == "-") {
            if (allowAppend) return size
            throw JsonPatchApplicationException("[$op Operation] '-' refers to a nonexistent array element, path: ${format(path)}")
        }
        if (!isIndexToken(token)) {
            throw JsonPatchApplicationException("[$op Operation] \"$token\" is not a valid array index, path: ${format(path)}")
        }
        val index = token.toIntOrNull()
        val max = if (allowAppend) size else size - 1
        if (index == null || index > max) {
            throw JsonPatchApplicationException("[$op Operation] index $token is out of bounds (array size $size), path: ${format(path)}")
        }
        return index
    }

    private fun isIndexToken(token: String): Boolean =
        token.isNotEmpty() && token.all { it in '0'..'9' } && (token.length == 1 || token[0] != '0')

    private fun decodeToken(token: String, pointer: String, member: String): String {
        if (token.indexOf('~') < 0) return token
        val decoded = StringBuilder(token.length)
        var i = 0
        while (i < token.length) {
            val c = token[i]
            if (c == '~') {
                when (token.getOrNull(i + 1)) {
                    '0' -> decoded.append('~')
                    '1' -> decoded.append('/')
                    else -> throw InvalidJsonPatchException(
                        "Invalid JSON Pointer in '$member': \"$pointer\" contains an invalid escape sequence ('~' must be followed by '0' or '1')",
                    )
                }
                i += 2
            } else {
                decoded.append(c)
                i++
            }
        }
        return decoded.toString()
    }
}
