# RootEncoder / MediaCodec
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.streamlink.**$$serializer { *; }
-keepclassmembers class app.streamlink.** {
    *** Companion;
}
-keepclasseswithmembers class app.streamlink.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
