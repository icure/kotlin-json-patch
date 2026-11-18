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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Data-driven test runner. Fixtures have the shape
 * `{ "errors": [ { "op", "node", "message"? } ], "ops": [ { "op", "node", "expected"?, "message"? } ] }`.
 *
 * Every "errors" case must throw [JsonPatchApplicationException] (or a subclass).
 * Every "ops" case must apply without error and, when "expected" is present, produce exactly that document.
 * All cases are executed and failures are reported together.
 */
abstract class AbstractTest {
    abstract fun data(): Collection<PatchTestCase>

    @Test
    fun test() {
        val failures = mutableListOf<String>()
        for (case in data()) {
            try {
                if (case.isOperation) testOperation(case) else testError(case)
            } catch (e: Throwable) {
                failures += "[${describe(case.getNode())}] ${e::class.simpleName}: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${data().size} cases failed:\n" + failures.joinToString("\n"))
        }
    }

    private fun testOperation(case: PatchTestCase) {
        val node = case.getNode()
        val source = node["node"]!!
        val patch = node["op"]!!.jsonArray
        val actual = JsonPatch.apply(patch, source)
        val expected = node["expected"]
        if (expected != null) {
            assertEquals(expected, actual, describe(node))
        }
    }

    private fun testError(case: PatchTestCase) {
        val node = case.getNode()
        val source = node["node"]!!
        val patch = node["op"]!!.jsonArray
        assertFailsWith<JsonPatchApplicationException>("expected failure: ${describe(node)}") {
            JsonPatch.apply(patch, source)
        }
    }

    private fun describe(node: JsonObject): String =
        node["message"]?.toString() ?: "patch=${node["op"]} node=${node["node"]}"
}
