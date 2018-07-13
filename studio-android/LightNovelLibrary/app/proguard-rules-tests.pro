# Proguard rules that are applied to your test apk/code.
-dontshrink
-dontoptimize
-dontpreverify

-keep class *.** { *; }
-dontwarn **

## proguard-test.pro:
#-include proguard-rules.pro
#-keepattributes SourceFile,LineNumberTable
