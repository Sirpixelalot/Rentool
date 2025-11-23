# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Chaquopy Python bridge - keep all Python-related classes
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep Python module wrapper classes (your Python bridge code)
-keepclassmembers class ** {
    *** getModule(...);
    *** callAttr(...);
}

# Keep all enum classes (needed for valueOf() in CompressionSettings)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# BouncyCastle crypto - keep algorithm names and providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepnames class org.bouncycastle.jcajce.provider.** { *; }
-keepnames class org.bouncycastle.jce.provider.** { *; }

# APKSig library - keep signing classes
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# FFmpeg-Kit (should have its own consumer rules, but add just in case)
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Keep data classes used for settings and configuration
-keep class com.renpytool.CompressionSettings { *; }
-keep class com.renpytool.VersionInfo { *; }
-keep class com.renpytool.keystore.** { *; }

# Keep UpdateChecker sealed class hierarchy
-keep class com.renpytool.UpdateChecker$** { *; }

# Keep ViewModels (shouldn't be obfuscated for easier debugging)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Gson (if used for JSON parsing)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Sora Editor - Keep all editor classes and TextMate language support
-keep class io.github.rosemoe.sora.** { *; }
-keep interface io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**

# TextMate language support (reflection-based grammar loading)
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.eclipse.tm4e.**

# Keep custom editor classes
-keep class com.renpytool.editor.** { *; }