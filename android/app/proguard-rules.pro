# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keep class com.bysel.trader.data.models.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepattributes RuntimeVisibleAnnotations

# Kotlin
-keepclassmembers class ** {
    *** Companion;
}
-keep class kotlin.Metadata { *; }

# Keep data classes
-keep class com.bysel.trader.data.models.** { *; }
-keep class com.bysel.trader.ui.** { *; }
