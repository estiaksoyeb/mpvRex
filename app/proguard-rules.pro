# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontobfuscate

# MPV Native & JNI Rules (Do NOT optimize as JNI callbacks rely on exact method signatures)
-keep class is.xyz.mpv.** { *; }
-keep class net.mediaarea.mediainfo.lib.** { *; }

# AndroidX Media & MediaSession ProGuard Rules
# Keeps MediaSessionCompat, MediaBrowserServiceCompat, MediaButtonReceiver, and IPC Callbacks
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.session.MediaButtonReceiver { *; }
-keep class * extends androidx.media.MediaBrowserServiceCompat { *; }
-keep class * extends android.support.v4.media.session.MediaSessionCompat$Callback { *; }

# Keep MediaPlaybackService and its inner classes/binders
-keep class xyz.mpv.rex.ui.player.MediaPlaybackService { *; }
-keep class xyz.mpv.rex.ui.player.MediaPlaybackService$** { *; }

# Keep Parcelables (Required for IPC between System UI Media Widget & Service)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Koin DI reflection rules
-keep class org.koin.** { *; }
-dontwarn org.koin.**

-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-dontwarn org.slf4j.impl.StaticLoggerBinder


# SMBJ ProGuard Rules
# Keep SMBJ classes
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.security.** { *; }

# JGSS (Kerberos/SPNEGO) - Optional, not needed for basic NTLM auth
# These are used for domain authentication, which we don't use
-dontwarn org.ietf.jgss.**
-dontnote org.ietf.jgss.**

# MBassador (event bus used by SMBJ) - EL (Expression Language) is optional
-dontwarn net.engio.mbassy.dispatch.el.**
-dontnote net.engio.mbassy.dispatch.el.**
-dontwarn javax.el.**
-dontnote javax.el.**

# Keep MBassador core classes
-keep class net.engio.mbassy.** { *; }

# BouncyCastle (crypto provider used by SMBJ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ASN.1 classes
-keep class com.hierynomus.asn1.** { *; }

# Keep all classes that use reflection or are loaded dynamically
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}