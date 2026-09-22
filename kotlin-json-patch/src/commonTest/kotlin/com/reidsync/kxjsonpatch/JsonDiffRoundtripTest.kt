package com.reidsync.kxjsonpatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Core contract of the diff side: for any two documents `a` and `b`,
 * `JsonPatch.apply(JsonDiff.asJson(a, b), a)` must succeed and equal `b`.
 */
class JsonDiffRoundtripTest {
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    private fun assertRoundtrip(source: JsonElement, target: JsonElement) {
        val patch = JsonDiff.asJson(source, target)
        val result = try {
            JsonPatch.apply(patch, source)
        } catch (e: Throwable) {
            fail("patch $patch generated for $source -> $target does not apply: ${e::class.simpleName}: ${e.message}")
        }
        assertEquals(target, result, "patch $patch generated for $source -> $target produced a different document")
    }

    @Test fun copyAfterRemoveInArrayProducesTarget() = assertRoundtrip(j("""["x","y","q"]"""), j("""["y","y","q"]"""))
    @Test fun copyAfterRemoveInArrayDoesNotCrash() = assertRoundtrip(j("""["x","y"]"""), j("""["y","y"]"""))
    @Test fun copyAfterRemoveInMixedArray() =
        assertRoundtrip(j("""["b",1]"""), j("""[1,1,[{"k0":null},{},"b"],{"k0":true}]"""))

    @Test fun copyOfUnchangedObjectMemberIsStillGenerated() {
        // Guard: the fix for array copies must not disable copy detection on stable object paths.
        val patch = JsonDiff.asJson(j("""{"a":"c"}"""), j("""{"a":"c","d":"c"}"""))
        assertEquals(j("""[{"op":"copy","from":"/a","path":"/d"}]"""), patch)
    }

    @Test fun randomIndependentDocumentsRoundtrip() {
        val rnd = Random(20260922)
        repeat(5000) {
            assertRoundtrip(randomJson(rnd, 3, emptyList()), randomJson(rnd, 3, emptyList()))
        }
    }

    @Test fun randomRelatedDocumentsRoundtrip() {
        // Target reuses subtrees of the source so move/copy detection is exercised heavily.
        val rnd = Random(7)
        repeat(5000) {
            val source = randomJson(rnd, 3, emptyList())
            val target = randomJson(rnd, 3, subtreesOf(source))
            assertRoundtrip(source, target)
        }
    }

    private fun randomJson(rnd: Random, depth: Int, pool: List<JsonElement>): JsonElement {
        if (pool.isNotEmpty() && rnd.nextInt(10) < 4) return pool[rnd.nextInt(pool.size)]
        return when (rnd.nextInt(if (depth <= 0) 6 else 10)) {
            0 -> JsonPrimitive(1)
            1 -> JsonPrimitive(2)
            2 -> JsonPrimitive("a")
            3 -> JsonPrimitive("b")
            4 -> JsonPrimitive(true)
            5 -> JsonNull
            6, 7 -> JsonArray(List(rnd.nextInt(5)) { randomJson(rnd, depth - 1, pool) })
            else -> JsonObject(
                List(rnd.nextInt(4)) { "k$it" }.filter { rnd.nextBoolean() }.associateWith { randomJson(rnd, depth - 1, pool) },
            )
        }
    }

    private fun subtreesOf(element: JsonElement): List<JsonElement> = buildList {
        fun walk(e: JsonElement) {
            add(e)
            when (e) {
                is JsonArray -> e.forEach(::walk)
                is JsonObject -> e.values.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
    }
}
