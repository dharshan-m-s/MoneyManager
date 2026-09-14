# ============================================================
# MoneyManager ProGuard/R8 Rules
# ============================================================

# --- Keep Gson TypeToken and generic type information ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Gson's generic type information used by TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

-dontwarn javax.annotation.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn javax.annotation.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Room entities and DAOs (accessed by Room compiler and reflection)
-keep class com.moneymanager.app.data.local.entity.** { *; }
-keep class com.moneymanager.app.data.local.dao.** { *; }
-keep class com.moneymanager.app.data.local.converter.Converters { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Enum types (stored by Room as strings) ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Timber (tree removed in release) ---
-keep class com.moneymanager.app.**Tree { *; }


# --- Remove Timber log calls in release builds ---
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# --- OkHttp (platform-specific warnings) ---
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# --- Updater models (Gson deserialization via reflection) ---
-keep class com.moneymanager.app.data.updater.UpdateModels$* { *; }
-keep class com.moneymanager.app.data.updater.UpdateModels { *; }
-keep class com.moneymanager.app.data.updater.GitHubRelease { *; }
-keep class com.moneymanager.app.data.updater.GitHubAsset { *; }
-keep class com.moneymanager.app.data.updater.UpdateMetadata { *; }
