# ── Stack traces ──────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Keep generated $$serializer companions for all @Serializable classes (including
# private inner classes in UpdateChecker and PairingRepository).
-keep,includedescriptorclasses class tw.bluehomewu.devicemonitor.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class tw.bluehomewu.devicemonitor.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# Retain the @Serializable classes themselves so R8 doesn't inline / remove fields
# referenced only via reflection by the serialization runtime.
-keep @kotlinx.serialization.Serializable class tw.bluehomewu.devicemonitor.** { *; }

# ── OkHttp (Ktor engine) ──────────────────────────────────────────────────────
# OkHttp 4.x bundles its own consumer rules; suppress residual warnings only.
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Google Credential Manager / Sign-In ───────────────────────────────────────
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
# WorkManager instantiates WorkDatabase and Worker subclasses by canonical name
# via reflection; R8 must not rename or remove them.
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
