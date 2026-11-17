# Wenku8 v2 (:app-v2) R8 / ProGuard rules
-keepattributes SourceFile,LineNumberTable

# Keep shared :api classes & enum names intact across reflection/JNI boundaries.
-keep class org.mewx.wenku8.api.** { *; }
-keep class org.mewx.wenku8.network.** { *; }
-keep class org.mewx.wenku8.service.** { *; }

# Keep Kotlinx Serialization runtime & generated serializers.
-keep class kotlinx.serialization.** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
