# ProGuard / R8 rules for the release build.
#
# AGP already bundles comprehensive defaults for Compose, Kotlin metadata, and
# AndroidX. Hilt and Room each ship their own consumer rules for their
# generated code, so we only add project-specific keeps here.

# --- Keep app source line numbers for usable crash reports ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Hilt / Dagger ----------------------------------------------------------
# Hilt's consumer rules handle generated components. Keep the app-level
# annotated entry points (Application / AndroidEntryPoint / HiltViewModel) by
# name since they are referenced reflectively at startup.
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.*
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# --- Room -------------------------------------------------------------------
# Room's consumer rules already cover @Entity / @Dao / @Database classes.
# These extras protect generated member access patterns.
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# --- WorkManager ------------------------------------------------------------
# Only CoroutineWorker is used in this project.
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Google AI Edge generative-ai (Gemini Nano) ----------------------------
# The SDK reflects into generated implementations on supported devices.
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.ai.edge.**
