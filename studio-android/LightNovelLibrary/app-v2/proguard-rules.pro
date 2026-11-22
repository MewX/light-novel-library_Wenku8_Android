# Wenku8 v2 (:app-v2) R8 / ProGuard rules
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Private :api JNI bridge keep rules are supplied exclusively by :api/consumer-rules.pro.

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
