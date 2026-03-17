# ProGuard rules for Chromecast Ultimate

# Keep application classes
-keep class com.example.castultimate.** { *; }

# Keep NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Keep Google Play Services Cast
-keep class com.google.android.gms.cast.** { *; }
-dontwarn com.google.android.gms.cast.**

# Keep AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep JSON classes
-keep class org.json.** { *; }
-dontwarn org.json.**

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# General Android
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
