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

/** The six RFC 6902 operations (section 4). Names are case-sensitive. */
internal enum class Operation(val rfcName: String) {
    ADD("add"),
    REMOVE("remove"),
    REPLACE("replace"),
    MOVE("move"),
    COPY("copy"),
    TEST("test");

    companion object {
        private val byName = entries.associateBy { it.rfcName }

        fun fromName(rfcName: String): Operation =
            byName[rfcName] ?: throw InvalidJsonPatchException("unknown / unsupported operation \"$rfcName\"")
    }
}

/** Member names of an RFC 6902 operation object. */
internal object PatchMember {
    const val OP = "op"
    const val PATH = "path"
    const val FROM = "from"
    const val VALUE = "value"
}
