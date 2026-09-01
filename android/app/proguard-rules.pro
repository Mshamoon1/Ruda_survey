# R8 / ProGuard rules for RUDA Survey
# Keep data classes used with Gson (Retrofit serialization)
-keep class com.ruda.survey.data.dto.** { *; }
-keep class com.ruda.survey.domain.model.** { *; }

# Room entities
-keep class com.ruda.survey.data.local.** { *; }

# Retrofit interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# Coroutines
-keepnames class kotlinx.coroutines.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
