# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/mewx/Programs/AndroidSDK/adt-bundle-linux-x86_64-20140702/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-libraryjars   libs/android-support-v4.jar
-dontwarn android.support.v4.**
-keep class android.support.v4.** { *; }
-keep interface android.support.v4.app.** { *; }

-libraryjars   libs/android-support-v7.jar
-dontwarn android.support.v7.**
-keep class android.support.v7.** { *; }
-keep interface android.support.v7.app.** { *; }

-keep class com.jpardogo.android.googleprogressbar.** { *; }
-dontwarn com.squareup.okhttp.**
