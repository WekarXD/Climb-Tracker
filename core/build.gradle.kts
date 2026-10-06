plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

// The version of the segmentation runtime; the app brings the Android build of the same one.
val onnxRuntime = "1.24.3"

dependencies {
    // Only its interface is needed here: whoever runs the model supplies the runtime.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:$onnxRuntime")

    testImplementation(kotlin("test"))
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$onnxRuntime")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    // The test that measures the silhouettes reads the models the app build downloads.
    dependsOn(":app:fetchModels")
}
