# ProGuard / R8 rules for the release build.
#
# AGP already bundles comprehensive defaults for Compose, Kotlin metadata, and
# AndroidX. The rules below cover what is specific to this project.

# --- Keep app source line numbers for usable crash reports ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Hilt / Dagger ----------------------------------------------------------
# The Hilt Gradle plugin ships its own consumer rules, but we keep generated
# entry points and modules defensively so reflection-driven init never breaks.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.*
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# --- Room -------------------------------------------------------------------
# Entities and DAOs are accessed via generated adapters. The names of the
# annotated classes and their fields must survive shrinking/renaming.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class *
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# --- WorkManager ------------------------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Kotlin / kotlinx.serialization ----------------------------------------
# These are preventative: we don't use kotlinx.serialization today, but keeping
# the stubs avoids surprises if a future phase pulls it in as a transitive.
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# --- Google AI Edge generative-ai (Gemini Nano) ----------------------------
# The SDK reflects into generated implementations on supported devices.
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.ai.edge.**
