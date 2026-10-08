# Keep JNI native entry points
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.introcarbon.beatrice.jni.BeatriceJni { *; }
-keep class com.introcarbon.beatrice.model.** { *; }
