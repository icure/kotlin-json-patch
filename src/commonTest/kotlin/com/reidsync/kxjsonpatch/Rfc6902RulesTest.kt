package com.reidsync.kxjsonpatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Executable specification of the RFC 6902 (JSON Patch) and RFC 6901 (JSON Pointer) rules
 * this library commits to. Section numbers refer to RFC 6902 unless stated otherwise.
 */
class Rfc6902RulesTest {
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun apply(doc: String, patch: String): JsonElement = JsonPatch.apply(j(patch), j(doc))
    private fun assertApplied(doc: String, patch: String, expected: String) =
        assertEquals(j(expected), apply(doc, patch), "applying $patch to $doc")
    private inline fun <reified T : Throwable> assertRejected(doc: String, patch: String): T =
        assertFailsWith<T>("applying $patch to $doc") { apply(doc, patch) }
    private fun assertPatchFails(doc: String, patch: String) = assertRejected<JsonPatchApplicationException>(doc, patch)
    private fun assertInvalidPatch(doc: String, patch: String) = assertRejected<InvalidJsonPatchException>(doc, patch)

    // ---- §3 document structure -------------------------------------------------------------

    @Test fun patchMustBeAnArray() = run { assertInvalidPatch("""{}""", """{"op":"add","path":"/a","value":1}"""); Unit }
    @Test fun operationMustBeAnObject() = run { assertInvalidPatch("""{}""", """[1]"""); Unit }
    @Test fun opMemberIsRequired() = run { assertInvalidPatch("""{}""", """[{"path":"/a","value":1}]"""); Unit }
    @Test fun unknownOpIsRejected() = run { assertInvalidPatch("""{}""", """[{"op":"spam","path":"/a","value":1}]"""); Unit }
    @Test fun opMustBeAString() = run { assertInvalidPatch("""{}""", """[{"op":1,"path":"/a"}]"""); Unit }
    @Test fun opNamesAreCaseSensitive() = run { assertInvalidPatch("""{}""", """[{"op":"ADD","path":"/a","value":1}]"""); Unit }
    @Test fun pathMemberIsRequired() = run { assertInvalidPatch("""{}""", """[{"op":"add","value":1}]"""); Unit }
    @Test fun unknownMembersAreIgnored() =
        assertApplied("""{"foo":1}""", """[{"op":"test","path":"/foo","value":1,"spurious":true}]""", """{"foo":1}""")
    @Test fun emptyPatchLeavesDocumentUnchanged() = assertApplied("""{"a":[1]}""", """[]""", """{"a":[1]}""")
    @Test fun valueIsRequiredForAddByDefault() = run { assertInvalidPatch("""{}""", """[{"op":"add","path":"/a"}]"""); Unit }
    @Test fun valueIsRequiredForReplaceByDefault() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"replace","path":"/a"}]"""); Unit }
    @Test fun valueIsRequiredForTestByDefault() = run { assertInvalidPatch("""{"a":null}""", """[{"op":"test","path":"/a"}]"""); Unit }
    @Test fun fromIsRequiredForMove() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"move","path":"/b"}]"""); Unit }
    @Test fun fromIsRequiredForCopy() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"copy","path":"/b"}]"""); Unit }

    @Test fun missingValueIsTreatedAsNullOnlyWithCompatibilityFlag() {
        val result = JsonPatch.apply(j("""[{"op":"add","path":"/a"}]"""), j("{}"), setOf(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
        assertEquals(j("""{"a":null}"""), result)
    }
    @Test fun validateRejectsMissingValueByDefault() {
        assertFailsWith<InvalidJsonPatchException> { JsonPatch.validate(j("""[{"op":"add","path":"/a"}]""")) }
    }
    @Test fun validateRejectsInvalidPointer() {
        assertFailsWith<InvalidJsonPatchException> { JsonPatch.validate(j("""[{"op":"remove","path":"a"}]""")) }
    }
    @Test fun validateAcceptsWellFormedPatch() {
        JsonPatch.validate(j("""[{"op":"add","path":"/a","value":1},{"op":"move","from":"/a","path":"/b"},{"op":"test","path":"","value":{}}]"""))
    }

    // ---- §4 / RFC 6901 JSON Pointer --------------------------------------------------------

    @Test fun pathMustBeAString() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"remove","path":5}]"""); Unit }
    @Test fun pathMustBeEmptyOrStartWithSlash() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"remove","path":"a"}]"""); Unit }
    @Test fun fromMustBeAString() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"move","from":null,"path":"/b"}]"""); Unit }
    @Test fun tildeEscapesAreDecodedInOrder() =
        assertApplied("""{"/":9,"~1":10}""", """[{"op":"test","path":"/~01","value":10}]""", """{"/":9,"~1":10}""")
    @Test fun slashEscapeIsDecoded() = assertApplied("""{"a/b":1}""", """[{"op":"remove","path":"/a~1b"}]""", """{}""")
    @Test fun invalidTildeEscapeIsRejected() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"remove","path":"/a~2"}]"""); Unit }
    @Test fun trailingTildeIsRejected() = run { assertInvalidPatch("""{"a":1}""", """[{"op":"remove","path":"/a~"}]"""); Unit }
    @Test fun keysContainingQuotesAreResolved() =
        assertApplied("""{"k\"l":6}""", """[{"op":"test","path":"/k\"l","value":6}]""", """{"k\"l":6}""")
    @Test fun keysContainingBackslashesAreResolved() =
        assertApplied("""{"i\\j":5}""", """[{"op":"remove","path":"/i\\j"}]""", """{}""")
    @Test fun keysContainingControlCharactersAreResolved() =
        assertApplied("""{"a\nb":1}""", """[{"op":"remove","path":"/a\nb"}]""", """{}""")
    @Test fun emptyStringKeyIsResolved() = assertApplied("""{"":1,"a":2}""", """[{"op":"remove","path":"/"}]""", """{"a":2}""")
    @Test fun emptyPointerRefersToWholeDocument() =
        assertApplied("""{"a":1}""", """[{"op":"replace","path":"","value":[1]}]""", """[1]""")
    @Test fun arrayIndexWithLeadingZeroIsRejected() = run { assertPatchFails("""["a","b"]""", """[{"op":"remove","path":"/01"}]"""); Unit }
    @Test fun arrayIndexMustBeDigits() = run { assertPatchFails("""["a","b"]""", """[{"op":"remove","path":"/1e0"}]"""); Unit }
    @Test fun arrayIndexMustNotBeNegative() = run { assertPatchFails("""["a","b"]""", """[{"op":"remove","path":"/-1"}]"""); Unit }
    @Test fun arrayIndexOutOfBoundsIsRejected() = run { assertPatchFails("""["a","b"]""", """[{"op":"remove","path":"/2"}]"""); Unit }
    @Test fun traversingThroughAPrimitiveIsRejected() = run { assertPatchFails("""{"a":1}""", """[{"op":"remove","path":"/a/b"}]"""); Unit }
    @Test fun numericLookingObjectKeysAreObjectKeys() = assertApplied("""{"0":"a"}""", """[{"op":"remove","path":"/0"}]""", """{}""")

    // ---- §4.1 add ---------------------------------------------------------------------------

    @Test fun addCreatesObjectMember() =
        assertApplied("""{"foo":"bar"}""", """[{"op":"add","path":"/baz","value":"qux"}]""", """{"foo":"bar","baz":"qux"}""")
    @Test fun addReplacesExistingObjectMember() = assertApplied("""{"foo":1}""", """[{"op":"add","path":"/foo","value":2}]""", """{"foo":2}""")
    @Test fun addInsertsIntoArrayShiftingElements() =
        assertApplied("""["a","c"]""", """[{"op":"add","path":"/1","value":"b"}]""", """["a","b","c"]""")
    @Test fun addAtIndexEqualToSizeAppends() = assertApplied("""["a"]""", """[{"op":"add","path":"/1","value":"b"}]""", """["a","b"]""")
    @Test fun addWithDashAppends() =
        assertApplied("""{"foo":["bar"]}""", """[{"op":"add","path":"/foo/-","value":["abc","def"]}]""", """{"foo":["bar",["abc","def"]]}""")
    @Test fun addBeyondArrayEndIsRejected() = run { assertPatchFails("""[0,1,2]""", """[{"op":"add","path":"/4","value":"x"}]"""); Unit }
    @Test fun addToRootReplacesDocument() =
        assertApplied("""{"foo":"bar"}""", """[{"op":"add","path":"","value":{"baz":"qux"}}]""", """{"baz":"qux"}""")
    @Test fun addToRootAcceptsScalar() = assertApplied("""{"foo":"bar"}""", """[{"op":"add","path":"","value":"x"}]""", "\"x\"")
    @Test fun addWithMissingParentIsRejected() =
        run { assertPatchFails("""{"foo":"bar"}""", """[{"op":"add","path":"/baz/bat","value":"qux"}]"""); Unit }
    @Test fun addWithMissingParentDoesNotCorruptExistingParent() {
        // Regression: a missing intermediate segment used to overwrite the existing parent with null.
        assertPatchFails("""{"a":{}}""", """[{"op":"add","path":"/a/b/c","value":1}]""")
        assertPatchFails("""{"arr":[{"x":1}]}""", """[{"op":"add","path":"/arr/0/y/z","value":1}]""")
    }
    @Test fun addWithPrimitiveParentIsRejected() = run { assertPatchFails("""{"foo":"bar"}""", """[{"op":"add","path":"/foo/f","value":1}]"""); Unit }
    @Test fun addNullValue() = assertApplied("""{}""", """[{"op":"add","path":"/a","value":null}]""", """{"a":null}""")

    // ---- §4.2 remove ------------------------------------------------------------------------

    @Test fun removeObjectMember() = assertApplied("""{"baz":"qux","foo":"bar"}""", """[{"op":"remove","path":"/baz"}]""", """{"foo":"bar"}""")
    @Test fun removeArrayElementShiftsLeft() =
        assertApplied("""{"foo":["bar","qux","baz"]}""", """[{"op":"remove","path":"/foo/1"}]""", """{"foo":["bar","baz"]}""")
    @Test fun removeMissingMemberIsRejected() = run { assertPatchFails("""{"x":{}}""", """[{"op":"remove","path":"/x/y"}]"""); Unit }
    @Test fun removeMissingTopLevelMemberIsRejected() = run { assertPatchFails("""{"x":1}""", """[{"op":"remove","path":"/y"}]"""); Unit }
    @Test fun removeDeepMissingPathIsRejected() = run { assertPatchFails("""{"foo":1}""", """[{"op":"remove","path":"/missing1/missing2"}]"""); Unit }
    @Test fun removeWithDashIsRejected() = run { assertPatchFails("""{"a":[1,2]}""", """[{"op":"remove","path":"/a/-"}]"""); Unit }
    @Test fun removeRootIsRejected() = run { assertPatchFails("""{"a":1}""", """[{"op":"remove","path":""}]"""); Unit }

    // ---- §4.3 replace -----------------------------------------------------------------------

    @Test fun replaceObjectMember() =
        assertApplied("""{"baz":"qux","foo":"bar"}""", """[{"op":"replace","path":"/baz","value":"boo"}]""", """{"baz":"boo","foo":"bar"}""")
    @Test fun replaceArrayElement() = assertApplied("""["a","b"]""", """[{"op":"replace","path":"/1","value":"c"}]""", """["a","c"]""")
    @Test fun replaceMissingMemberIsRejected() = run { assertPatchFails("""{"x":{}}""", """[{"op":"replace","path":"/x/y","value":42}]"""); Unit }
    @Test fun replaceMissingTopLevelMemberIsRejected() = run { assertPatchFails("""{"x":1}""", """[{"op":"replace","path":"/y","value":42}]"""); Unit }
    @Test fun replaceMissingParentIsRejected() = run { assertPatchFails("""{"bar":"baz"}""", """[{"op":"replace","path":"/foo/bar","value":false}]"""); Unit }
    @Test fun replaceArrayIndexOutOfBoundsIsRejected() = run { assertPatchFails("""["a"]""", """[{"op":"replace","path":"/1","value":"c"}]"""); Unit }
    @Test fun replaceWithDashIsRejected() = run { assertPatchFails("""["a"]""", """[{"op":"replace","path":"/-","value":"c"}]"""); Unit }
    @Test fun replaceRootWithScalar() = assertApplied("""{"foo":"bar"}""", """[{"op":"replace","path":"","value":"bar"}]""", "\"bar\"")

    // ---- §4.4 move --------------------------------------------------------------------------

    @Test fun moveObjectMember() = assertApplied(
        """{"foo":{"bar":"baz","waldo":"fred"},"qux":{"corge":"grault"}}""",
        """[{"op":"move","from":"/foo/waldo","path":"/qux/thud"}]""",
        """{"foo":{"bar":"baz"},"qux":{"corge":"grault","thud":"fred"}}""",
    )
    @Test fun moveArrayElement() = assertApplied(
        """{"foo":["all","grass","cows","eat"]}""",
        """[{"op":"move","from":"/foo/1","path":"/foo/3"}]""",
        """{"foo":["all","cows","eat","grass"]}""",
    )
    @Test fun moveNullValue() = assertApplied("""{"foo":null}""", """[{"op":"move","from":"/foo","path":"/bar"}]""", """{"bar":null}""")
    @Test fun moveToSameLocationHasNoEffect() = assertApplied("""{"foo":1}""", """[{"op":"move","from":"/foo","path":"/foo"}]""", """{"foo":1}""")
    @Test fun moveMissingFromIsRejected() = run { assertPatchFails("""{}""", """[{"op":"move","from":"/a","path":"/b"}]"""); Unit }
    @Test fun moveIntoOwnChildIsRejected() =
        run { assertPatchFails("""{"foo":{"bar":"baz"}}""", """[{"op":"move","from":"/foo","path":"/foo/bar"}]"""); Unit }
    @Test fun moveToMissingParentIsRejected() = run { assertPatchFails("""{"a":"b"}""", """[{"op":"move","from":"/a","path":"/b/c"}]"""); Unit }
    @Test fun moveWithBadFromIndexIsRejected() = run { assertPatchFails("""{"baz":[1,2,3]}""", """[{"op":"move","from":"/baz/1e0","path":"/boo"}]"""); Unit }

    // ---- §4.5 copy --------------------------------------------------------------------------

    @Test fun copyArrayElementToObjectMember() = assertApplied(
        """{"baz":[{"qux":"hello"}],"bar":1}""",
        """[{"op":"copy","from":"/baz/0","path":"/boo"}]""",
        """{"baz":[{"qux":"hello"}],"bar":1,"boo":{"qux":"hello"}}""",
    )
    @Test fun copyNullValue() = assertApplied("""{"foo":null}""", """[{"op":"copy","from":"/foo","path":"/bar"}]""", """{"foo":null,"bar":null}""")
    @Test fun copyIntoOwnChildIsAllowed() =
        assertApplied("""{"a":{"x":1}}""", """[{"op":"copy","from":"/a","path":"/a/b"}]""", """{"a":{"x":1,"b":{"x":1}}}""")
    @Test fun copyMissingFromIsRejected() = run { assertPatchFails("""{"foo":1}""", """[{"op":"copy","from":"/bar","path":"/foo"}]"""); Unit }
    @Test fun copyWithBadFromIndexIsRejected() = run { assertPatchFails("""{"baz":[1,2,3]}""", """[{"op":"copy","from":"/baz/1e0","path":"/boo"}]"""); Unit }

    // ---- §4.6 test --------------------------------------------------------------------------

    @Test fun testSucceedsOnEqualValue() = assertApplied(
        """{"baz":"qux","foo":["a",2,"c"]}""",
        """[{"op":"test","path":"/baz","value":"qux"},{"op":"test","path":"/foo/1","value":2}]""",
        """{"baz":"qux","foo":["a",2,"c"]}""",
    )
    @Test fun testFailsOnDifferentValue() = run { assertPatchFails("""{"baz":"qux"}""", """[{"op":"test","path":"/baz","value":"bar"}]"""); Unit }
    @Test fun testDistinguishesStringsFromNumbers() =
        run { assertPatchFails("""{"/":9,"~1":10}""", """[{"op":"test","path":"/~01","value":"10"}]"""); Unit }
    @Test fun testComparesNumbersNumerically() {
        assertApplied("""{"a":1.0}""", """[{"op":"test","path":"/a","value":1}]""", """{"a":1.0}""")
        assertApplied("""{"a":1}""", """[{"op":"test","path":"/a","value":1e0}]""", """{"a":1}""")
        assertApplied("""{"a":10}""", """[{"op":"test","path":"/a","value":10.0}]""", """{"a":10}""")
        assertPatchFails("""{"a":1}""", """[{"op":"test","path":"/a","value":1.5}]""")
    }
    @Test fun testComparesObjectsRegardlessOfMemberOrder() =
        assertApplied("""{"foo":{"foo":1,"bar":2}}""", """[{"op":"test","path":"/foo","value":{"bar":2,"foo":1}}]""", """{"foo":{"foo":1,"bar":2}}""")
    @Test fun testComparesNestedStructures() =
        assertApplied("""{"foo":[{"foo":1,"bar":2}]}""", """[{"op":"test","path":"/foo","value":[{"bar":2,"foo":1}]}]""", """{"foo":[{"foo":1,"bar":2}]}""")
    @Test fun testFailsOnDifferentArrayLength() =
        run { assertPatchFails("""{"foo":{"bar":[1,2,5,4]}}""", """[{"op":"test","path":"/foo","value":[1,2]}]"""); Unit }
    @Test fun testFailsOnExtraObjectMember() = run { assertPatchFails("""{"foo":{"a":1}}""", """[{"op":"test","path":"/foo","value":{"a":1,"b":2}}]"""); Unit }
    @Test fun testNullEqualsNull() = assertApplied("""{"foo":null}""", """[{"op":"test","path":"/foo","value":null}]""", """{"foo":null}""")
    @Test fun testNullDoesNotEqualMissingMember() = run { assertPatchFails("""{"foo":1}""", """[{"op":"test","path":"/bar","value":null}]"""); Unit }
    @Test fun testBooleans() {
        assertApplied("""{"a":true}""", """[{"op":"test","path":"/a","value":true}]""", """{"a":true}""")
        assertPatchFails("""{"a":true}""", """[{"op":"test","path":"/a","value":false}]""")
    }
    @Test fun testWholeDocumentSucceeds() = assertApplied("""{"foo":1}""", """[{"op":"test","path":"","value":{"foo":1}}]""", """{"foo":1}""")
    @Test fun testWholeDocumentFailsInsteadOfReplacingIt() = run { assertPatchFails("""{"a":1}""", """[{"op":"test","path":"","value":{"b":2}}]"""); Unit }
    @Test fun testArrayIndexOutOfBoundsIsRejected() = run { assertPatchFails("""{"a":[1,2]}""", """[{"op":"test","path":"/a/2","value":1}]"""); Unit }
    @Test fun testWithDashIsRejected() = run { assertPatchFails("""{"a":[1,2]}""", """[{"op":"test","path":"/a/-","value":2}]"""); Unit }
    @Test fun testWithBadIndexIsRejected() = run { assertPatchFails("""["foo","bar"]""", """[{"op":"test","path":"/1e0","value":"bar"}]"""); Unit }

    // ---- §5 error handling: atomicity ------------------------------------------------------

    @Test fun failingOperationLeavesSourceUntouched() {
        val source = j("""{"a":1,"b":[1,2]}""")
        val patch = j("""[{"op":"add","path":"/c","value":3},{"op":"remove","path":"/missing"}]""")
        assertFailsWith<JsonPatchApplicationException> { JsonPatch.apply(patch, source) }
        assertEquals(j("""{"a":1,"b":[1,2]}"""), source)
    }
    @Test fun earlierOperationsAreVisibleToLaterOnes() = assertApplied(
        """{}""",
        """[{"op":"add","path":"/a","value":{}},{"op":"add","path":"/a/b","value":1},{"op":"test","path":"/a/b","value":1}]""",
        """{"a":{"b":1}}""",
    )
    @Test fun invalidOperationLaterInPatchFailsWholePatch() =
        run { assertInvalidPatch("""{}""", """[{"op":"add","path":"/a","value":1},{"op":"bogus","path":"/a"}]"""); Unit }

    // ---- exception contract -----------------------------------------------------------------

    @Test fun everyFailureIsReportedAsJsonPatchApplicationException() {
        val cases = listOf(
            """{"a":[1,2]}""" to """[{"op":"remove","path":"/a/x"}]""",
            """{"a":[1,2]}""" to """[{"op":"remove","path":"/a/-"}]""",
            """{"a":[1]}""" to """[{"op":"add","path":"/a/5/b","value":1}]""",
            """{}""" to """[{"op":"move","from":"/a","path":"/b"}]""",
            """{}""" to """[{"op":"copy","from":"/a","path":"/b"}]""",
            """{"a":[1,2]}""" to """[{"op":"test","path":"/a/2","value":1}]""",
            """[1]""" to """[{"op":"replace","path":"/x","value":1}]""",
            """{"a":1}""" to """[{"op":"remove","path":5}]""",
        )
        for ((doc, patch) in cases) {
            val e = runCatching { apply(doc, patch) }.exceptionOrNull()
            assertNotNull(e, "expected failure applying $patch to $doc")
            assertTrue(
                e is JsonPatchApplicationException,
                "expected JsonPatchApplicationException applying $patch to $doc, got ${e::class.simpleName}: ${e.message}",
            )
        }
    }
}
