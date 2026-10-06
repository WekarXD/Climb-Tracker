# The segmentation runtime calls back into these classes from native code, by name.
-keep class ai.onnxruntime.** { *; }

# Names in crash traces stay readable; the app has nothing to hide.
-dontobfuscate
