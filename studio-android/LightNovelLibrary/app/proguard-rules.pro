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

# general
-target 1.7
-optimizationpasses 5
#-dontoptimize
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-verbose
-dump class_files.txt
-printseeds seeds.txt
-printusage unused.txt
-printmapping mapping.txt

# universal
#-keepnames class * implements java.io.Serializable
#-keep public class * extends android.app.Activity
#-keep public class * extends android.support.v7.app.AppCompatActivity
#-keep public class * extends android.support.v4.app.Fragment
#-keep public class * extends android.app.Application
#-keep public class * extends android.app.Service
#-keep public class * extends android.content.BroadcastReceiver
#-keep public class * extends android.content.ContentProvider
#-keep public class * extends android.preference.Preference
#-keep public class com.android.vending.billing.IInAppBillingService
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet);
#}
#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet, int);
#}
#-keepclassmembers class * extends android.content.Context {
#    public void *(android.view.View);
#    public void *(android.view.MenuItem);
#}
#-keepclassmembers enum * {
#    public static **[] values();
#    public static ** valueOf(java.lang.String);
#}

# Apache
-dontwarn org.apache.commons.**
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**

# support v4
#-dontwarn android.support.v4.**
#-keep class android.support.v4.** { *; }
#-keep interface android.support.v4.app.** { *; }

# support v7
#-dontwarn android.support.v7.**
#-keep class android.support.v7.** { *; }
#-keep interface android.support.v7.** { *; }

# support design
#-dontwarn android.support.design.**
#-keep class android.support.design.** { *; }
#-keep interface android.support.design.** { *; }
#-keep public class android.support.design.R$* { *; }

# serializable
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable { *; }

# umeng self
-dontwarn com.umeng.**
-keep public class * extends com.umeng.**
-keep class com.umeng.** { *; }
-keep class com.alimama.** { *; }
-keep public class com.umeng.fb.ui.ThreadView { }

# umeng reflection
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}
-keep public class org.mewx.wenku8.R$* {
    public static final int *;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# volley
#-keep class com.android.volley.** { *; }
#-keep class com.android.volley.toolbox.** { *; }
#-keep class com.android.volley.Response$* { *; }
#-keep class com.android.volley.Request$* { *; }
#-keep class com.android.volley.RequestQueue$* { *; }
#-keep class com.android.volley.toolbox.HurlStack$* { *; }
#-keep class com.android.volley.toolbox.ImageLoader$* { *; }

# system bar tint

# universal image loader
#-keep class com.nostra13.universalimageloader.** { *; }
#-keepclassmembers class com.nostra13.universalimageloader.** { *; }

# material dialogs
#-keep class com.afollestad.materialdialogs.** { *; }
#-keep interface com.afollestad.materialdialogs.** { *; }

# floating action button
-keep class com.squareup.picasso.** { *; }
-dontwarn com.makeramen.roundedimageview.**
-keep class com.makeramen.roundedimageview.** { *; }
-keep interface com.makeramen.roundedimageview.** { *; }

# rounded image view

# subsampling scale image view

# smooth progressbar

# google progressbar
#-keep class com.jpardogo.android.googleprogressbar.** { *; }

# slider lib
#-keep class org.mewx.wenku8.reader.** { *; }
#-keep interface org.mewx.wenku8.reader.** { *; }

