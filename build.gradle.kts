import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.artifactRegistry)
}

// Set by the-forge when it publishes a release (-PgitTag=x.y.z, taken from the tag it was triggered by).
// Local builds are snapshots; pass -PgitTag=<version> to publish a specific version to the local Maven repository.
val gitTag: String? by project

group = "com.icure"
version = gitTag ?: "0.0.1-SNAPSHOT"

fun Project.getLocalProperties() =
    Properties().apply {
        kotlin.runCatching {
            load(rootProject.file("local.properties").reader())
        }
    }

kotlin {
    val localProperties = getLocalProperties()

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }
    android {
        namespace = "com.reidsync.kxjsonpatch"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
        withHostTestBuilder { }
    }

    val iosSimulators = listOf(
        iosX64(),
        iosSimulatorArm64()
    )
    iosArm64()
    iosSimulators.forEach { target ->
        target.testRuns.forEach { testRun ->
            // Optional `ios.simulator=<device name>` in local.properties selects the simulator used by the iOS tests.
            (localProperties["ios.simulator"] as? String)?.let { testRun.deviceId = it }
        }
    }
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()
    js(IR) {
        nodejs()
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // JsonElement is part of the public API, so consumers need kotlinx-serialization on their compile classpath.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events = setOf(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
    }
}

mavenPublishing {
    coordinates(group.toString(), rootProject.name, version.toString())

    pom {
        name = rootProject.name
        description = "RFC 6902 JSON Patch and JSON diff for Kotlin Multiplatform, built on kotlinx.serialization"
        inceptionYear = "2023"
        url = "https://github.com/icure/kotlin-json-patch"
        licenses {
            license {
                name = "Apache-2.0 license"
                url = "https://choosealicense.com/licenses/apache-2.0/"
                distribution = "https://choosealicense.com/licenses/apache-2.0/"
            }
        }
        developers {
            developer {
                id = "icure"
                name = "iCure"
                url = "https://github.com/iCure/"
            }
        }
        scm {
            url = "https://github.com/icure/kotlin-json-patch"
            connection = "scm:git:git://github.com/icure/kotlin-json-patch.git"
            developerConnection = "scm:git:ssh://git@github.com:icure/kotlin-json-patch.git"
        }
    }
}

// Published to Google Artifact Registry. The artifactregistry:// scheme lets the Artifact Registry plugin authenticate
// with Application Default Credentials, then gcloud: on the-forge, GOOGLE_APPLICATION_CREDENTIALS points at the key of
// a service account allowed to write to the repository. Builds that do not publish need no credentials.
publishing {
    repositories {
        maven {
            name = "ArtifactRegistry"
            url = uri("artifactregistry://europe-west1-maven.pkg.dev/taktik-icure/kotlin-json-patch")
        }
    }
}
