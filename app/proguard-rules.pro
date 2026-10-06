# The segmentation runtime calls back into these classes from native code, by name.
-keep class ai.onnxruntime.** { *; }

# The map view reads its configuration and tile sources by reflection.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Names in crash traces stay readable; the app has nothing to hide.
-dontobfuscate
