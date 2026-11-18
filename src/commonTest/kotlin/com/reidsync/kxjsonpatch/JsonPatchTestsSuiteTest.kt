package com.reidsync.kxjsonpatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import resources.testdata.TestData_JSON_PATCH_SPEC_TESTS
import resources.testdata.TestData_JSON_PATCH_TESTS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Runs the community JSON Patch conformance suite (https://github.com/json-patch/json-patch-tests).
 * Cases flagged `"disabled": true` upstream are skipped, as the suite intends.
 */
class JsonPatchTestsSuiteTest {

    @Test
    fun rfc6902AppendixAExamples() = runSuite(TestData_JSON_PATCH_SPEC_TESTS)

    @Test
    fun communityConformanceCases() = runSuite(TestData_JSON_PATCH_TESTS)

    private fun runSuite(fixture: String) {
        val cases = Json.parseToJsonElement(fixture).jsonArray.map { it.jsonObject }
        val failures = mutableListOf<String>()
        var executed = 0
        for (case in cases) {
            if (case["disabled"]?.jsonPrimitive?.booleanOrNull == true) continue
            executed++
            val comment = case["comment"]?.jsonPrimitive?.content ?: "patch=${case["patch"]} doc=${case["doc"]}"
            val doc = case["doc"]!!
            val patch = case["patch"]!!
            try {
                if (case.containsKey("error")) {
                    assertFailsWith<JsonPatchApplicationException>("expected error '${case["error"]}'") {
                        JsonPatch.apply(patch, doc)
                    }
                } else {
                    val actual = JsonPatch.apply(patch, doc)
                    case["expected"]?.let { assertEquals(it, actual) }
                }
            } catch (e: Throwable) {
                failures += "[$comment] ${e::class.simpleName}: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of $executed json-patch-tests cases failed:\n" + failures.joinToString("\n"))
        }
    }
}
