# [kotlin-json-patch]
[![Test](https://github.com/icure/kotlin-json-patch/actions/workflows/test.yml/badge.svg)](https://github.com/icure/kotlin-json-patch/actions/workflows/test.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-white.svg?logo=kotlin&color=6A5ACD)](http://kotlinlang.org/)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?logo=apache)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Maven Central](https://img.shields.io/maven-central/v/com.icure/kotlin-json-patch?logo=sonatype&logoColor=D2691E&color=D2691E)](https://central.sonatype.com/artifact/com.icure/kotlin-json-patch/overview)

![badge-support-kotlin-multiplatform]
![badge-support-android-native]
![badge-support-apple-silicon]
<a href="https://android-arsenal.com/api?level=23"><img alt="API" src="https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat"/></a>  
![badge-platform-android]
![badge-platform-ios]
![badge-platform-jvm]
![badge-platform-js]
![badge-platform-macos]
![badge-platform-linux]
![badge-platform-windows]

## Kotlin JSON Patching Library

### This is an implementation of [RFC 6902 JSON Patch](https://datatracker.ietf.org/doc/html/rfc6902) written exclusively in Kotlin.
It is based on the [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) licensed library from Flipkart, [zjsonpatch](https://github.com/flipkart-incubator/zjsonpatch).  
This project is a fork of [KJsonPatch](https://github.com/beyondeye/kjsonpatch) (with the [latest commit referenced](https://github.com/beyondeye/kjsonpatch/commit/939455832a09de666d9578963676996b5e09b6be)).
This repository is [iCure](https://github.com/icure)'s fork of [ReidSync/kotlin-json-patch](https://github.com/ReidSync/kotlin-json-patch): it fixes the RFC 6902 conformance issues listed below and is published under the `com.icure` group. See [NOTICE](NOTICE) for the attribution.

## Changes

This code has been modified from the original library in the following ways:
* Ported from Java to Kotlin
* Changed package names
* Substituted Gson dependency with [`kotlinx.serialization.json`](https://kotlinlang.org/api/latest/kotlin.test/)
* Added extensions for convenient usage of [`kotlinx.serialization.json`](https://kotlinlang.org/api/latest/kotlin.test/)

## Setup
The library is published on Maven Central under the `com.icure` group, as a Kotlin Multiplatform library (JVM, Android,
iOS, macOS, Linux, Windows and JS targets).

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.icure:kotlin-json-patch:${kotlin_json_patch_version}")
}
```
> _**Check the [kotlin-json-patch versions](https://central.sonatype.com/artifact/com.icure/kotlin-json-patch/versions)**_  
latest version : [![Maven Central](https://img.shields.io/maven-central/v/com.icure/kotlin-json-patch)](https://central.sonatype.com/artifact/com.icure/kotlin-json-patch/overview)  

You can add the dependency to `sourceSets.commonMain.dependencies` for your Kotlin Multiplatform project.

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.icure:kotlin-json-patch:${kotlin_json_patch_version}")
        }
    }
}
```

The library exposes `kotlinx.serialization`'s `JsonElement` and declares `kotlinx-serialization-json` as an `api`
dependency, so you do not need to add it yourself.

## API Usage

> The variables `source`, `target`, and `patch` below must be asserted as valid `JsonElement` objects. 
### Generating JSON Diff as a patch
```kotlin
val diff: JsonArray = JsonDiff.asJson(source: JsonElement, target: JsonElement)
```
or
```kotlin
val diff: JsonElement = source.generatePatch(with: target)
```
### Applying JSON patch
```kotlin
val result: JsonElement = JsonPatch.apply(patch: JsonElement, source: JsonElement)
```
or
```kotlin
val result: JsonElement = source.apply(patch: patch)
```
This operation is performed on a clone of the source object.

## RFC 6902 compliance

The patch engine follows [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902) and [RFC 6901](https://datatracker.ietf.org/doc/html/rfc6901) strictly.
The rules are pinned down by `Rfc6902RulesTest` and by the community [json-patch-tests](https://github.com/json-patch/json-patch-tests) conformance suite, both in `commonTest`.

* A patch is a JSON array of operation objects. `op` and `path` are required, `from` is required for `move`/`copy`,
  and `value` is required for `add`/`replace`/`test`. Operation names are case-sensitive; unknown members are ignored.
* JSON Pointers must be `""` (whole document) or start with `/`. `~0` and `~1` are decoded; any other `~` sequence is rejected.
  Array indices are digits without leading zeros. `-` (append) is only valid as the target of `add`.
* `add` requires the parent location to exist. `remove`, `replace`, `test` and the `from` of `move`/`copy` require the
  target location to exist. `move` rejects a `path` located inside `from`. Removing the whole document is rejected.
* `test` compares values as defined in section 4.6: numbers numerically (`1`, `1.0` and `1e0` are equal), strings by
  characters, objects regardless of member order.
* Any failure throws `JsonPatchApplicationException`; a malformed patch document throws its subclass
  `InvalidJsonPatchException`. Documents are immutable, so a failed patch leaves the source untouched (section 5).
* `JsonDiff.asJson(source, target)` always produces a patch that applies to `source` and yields `target`.

### Compatibility flags

`CompatibilityFlags.defaults()` is empty, i.e. strict. Pass `setOf(CompatibilityFlags.MISSING_VALUES_AS_NULLS)` to
`JsonPatch.apply` / `JsonPatch.validate` to treat a missing `value` member as JSON `null` instead of rejecting the patch.

### Breaking changes compared to 1.0.0

* Missing `value` members are rejected by default (previously treated as `null`).
* Pointers without a leading `/`, invalid `~` escapes, non-string `path`/`from`, uppercase operation names and array indices
  with leading zeros are rejected (previously accepted or silently ignored).
* Operations on non-existent locations fail instead of being silently ignored or corrupting the parent.
* `move` into a child of `from` fails. `test` on the whole document compares instead of replacing it.
* Failures that used to surface as `NumberFormatException`, `IndexOutOfBoundsException` or `NullPointerException`
  are now `JsonPatchApplicationException`.
* `JsonPatchEditingContext` gained a `document` property; `JsonPatchEditingContextTestImpl` and the unused
  `JsonPatchProcessor` interface were removed.

## Development

Requirements: JDK 17 or newer, the Android SDK (`ANDROID_HOME` or `sdk.dir` in `local.properties`) and, for the Apple
targets, Xcode. An optional `ios.simulator=<device name>` entry in `local.properties` selects the simulator used by the
iOS tests.

```shell
./gradlew jvmTest testAndroidHostTest jsNodeTest      # host tests, also run by the CI on every pull request
./gradlew macosArm64Test iosSimulatorArm64Test        # native tests (macOS only)
./gradlew allTests                                    # everything the current host can run
```

## Testing a build in another project

Publish the library to the local Maven repository and depend on it from there:

```shell
./gradlew publishToMavenLocal                 # publishes com.icure:kotlin-json-patch:0.0.1-SNAPSHOT
./gradlew publishToMavenLocal -PgitTag=2.0.0  # publishes that version instead
```

The consuming project needs `mavenLocal()` in its repositories, before `mavenCentral()`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Releasing

The library is released to [Maven Central](https://central.sonatype.com/artifact/com.icure/kotlin-json-patch) through
the [Central Portal](https://central.sonatype.org/publish/publish-portal-gradle/), built on the iCure build machine
driven by the private `icure/the-forge` repository, which holds this repository as a submodule. This is the same path
the Cardinal SDK, charix and fhir-models take.

To release:

1. Publish a GitHub release here, tagged with a bare version such as `2.0.0` (no `v` prefix: the tag name becomes the
   published version).
2. [`trigger-forge.yml`](.github/workflows/trigger-forge.yml) moves the submodule on the-forge to the released commit
   and tags it `kotlin-json-patch-2.0.0`.
3. That tag triggers the-forge's "Publish kotlin-json-patch" workflow, which runs
   `./gradlew publishAllPublicationsToMavenCentralRepository --no-configuration-cache -PgitTag=2.0.0` on the build
   machine and releases the deployment once the Central Portal validates it.

Maven Central versions are immutable, so a version can never be republished.

This repository needs one secret, `THE_FORGE_CI_PAT`, a token allowed to push to `icure/the-forge`. The Central Portal
credentials and the signing key live on the-forge, as they do for the other published libraries.

A release can also be produced from a workstation, bypassing the-forge:

```shell
./gradlew publishAllPublicationsToMavenCentralRepository --no-configuration-cache -PgitTag=<version> \
    -PmavenCentralUsername=... -PmavenCentralPassword=... \
    -PsigningInMemoryKey=... -PsigningInMemoryKeyId=... -PsigningInMemoryKeyPassword=...
```

Publishing is refused when the signing properties are missing, since the Central Portal rejects unsigned artifacts.


## 
These changes mostly involve porting from Java to Kotlin to transform it into a pure Kotlin library that can be imported into Kotlin Multiplatform. If you have any specific preferences or further adjustments, feel free to let me know!

<!--
![badge-platform-js-node]
![badge-platform-linux]
![badge-platform-macos]
![badge-platform-tvos]
![badge-platform-watchos]
![badge-platform-wasm]
![badge-platform-windows]

![badge-support-js-ir]
![badge-support-linux-arm]
-->

<!-- TAG_PLATFORMS -->
[badge-platform-android]: https://img.shields.io/badge/-android-6EDB8D.svg?logo=android&&logoColor=white&style=flat
[badge-platform-jvm]: https://img.shields.io/badge/-jvm-DB413D.svg?logo=jvm&logoColor=white&style=flat
[badge-platform-js]: https://img.shields.io/badge/-js-F8DB5D.svg?logo=JavaScript&logoColor=white&style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?logo=nodedotjs&logoColor=white&style=flat
[badge-platform-linux]: https://img.shields.io/badge/-linux-2D3F6C.svg?logo=linux&logoColor=white&style=flat
[badge-platform-macos]: https://img.shields.io/badge/-macos-111111.svg?logo=macOS&logoColor=white&style=flat
[badge-platform-ios]: https://img.shields.io/badge/-ios-CDCDCD.svg?logo=iOS&logoColor=white&style=flat
[badge-platform-tvos]: https://img.shields.io/badge/-tvos-808080.svg?logo=AppleTV&logoColor=white&style=flat
[badge-platform-watchos]: https://img.shields.io/badge/-watchos-C0C0C0.svg?logo=Apple&logoColor=white&style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?logo=webassembly&logoColor=white&style=flat
[badge-platform-windows]: https://img.shields.io/badge/-windows-4D76CD.svg?logo=Windows&logoColor=whitestyle=flat
[badge-support-android-native]: https://img.shields.io/badge/support-Android%20Native-6EDB8D.svg?style=flat?fontColor=white
[badge-support-apple-silicon]: https://img.shields.io/badge/support-Apple%20Silicon-808080.svg?style=flat
[badge-support-kotlin-multiplatform]: https://img.shields.io/badge/support-Kotlin%20Multiplatform-6A5ACD.svg?style=flat
[badge-support-js-ir]: https://img.shields.io/badge/support-[js--IR]-AAC4E0.svg?style=flat
[badge-support-linux-arm]: https://img.shields.io/badge/support-[LinuxArm]-2D3F6C.svg?style=flat

