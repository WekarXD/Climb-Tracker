import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.climbtracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.climbtracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 42
        versionName = "2.4.0"

        // -Ppruebas builds a copy that installs next to the real app, with its own data, to
        // try a change on a phone without touching the boulders kept in it.
        val trial = project.hasProperty("pruebas")
        if (trial) applicationIdSuffix = ".pruebas"
        manifestPlaceholders["appLabel"] = if (trial) "Climb Tracker (pruebas)" else "Climb Tracker"

        // The segmentation runtime ships native code; phones are 64-bit ARM.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        // The models are read whole from the assets, which is faster when they are stored as they are.
        noCompress += "onnx"
    }

    // CI signs with a keystore supplied through the environment, so its APKs install over
    // earlier ones. Local builds keep the default debug key of the machine.
    val ciKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")
    if (ciKeystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                // Optional: the alias defaults to the one the keystore is created with, and a
                // PKCS12 keystore uses its own password for the key.
                keyAlias = System.getenv("SIGNING_KEY_ALIAS").takeUnless { it.isNullOrEmpty() } ?: "climbtracker"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD").takeUnless { it.isNullOrEmpty() } ?: storePassword
            }
        }
    }
    val signing = signingConfigs.getByName(if (ciKeystore != null) "ci" else "debug")

    buildTypes {
        getByName("debug") {
            signingConfig = signing
        }
        // The published APK: shrunk and optimised. It keeps the key of the debug builds
        // published before it, so it installs over them.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

ksp {
    // The schema of each database version is kept in the repository, as a record for migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Runs the segmentation model that draws the outline of the holds.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")

    // OpenStreetMap map view, for finding gyms.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The segmentation models are too large to keep in the repository and change with each
// retraining. They are attached to a release of the project, and fetched from there the first
// time they are missing or when the checksums below change.
val modelsRelease = "models-1"
val models = mapOf(
    "sam_encoder.onnx" to "0f7767fafee4544e99741ce2c010927bcc2969a71e5dd19a6be5a066192c5e1a",
    "sam_decoder.onnx" to "18c8f412d45db4f27bffc3251fc5157e979d30aea9675fca916d3758d689174a",
)

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val fetchModels by tasks.registering {
    val assets = layout.projectDirectory.dir("src/main/assets").asFile
    val release = modelsRelease
    val wanted = models
    inputs.property("models", wanted)
    outputs.files(wanted.keys.map { File(assets, it) })
    doLast {
        assets.mkdirs()
        for ((name, checksum) in wanted) {
            val file = File(assets, name)
            if (file.exists() && sha256(file) == checksum) continue
            val url = "https://github.com/WekarXD/Climb-Tracker/releases/download/$release/$name"
            val partial = File(assets, "$name.part")
            try {
                URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            } catch (e: java.io.IOException) {
                partial.delete()
                throw GradleException("Could not download $url: ${e.message}", e)
            }
            if (sha256(partial) != checksum) {
                partial.delete()
                throw GradleException("$name from the release $release is not the file this version expects.")
            }
            file.delete()
            check(partial.renameTo(file)) { "Could not move $name into place." }
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchModels) }
