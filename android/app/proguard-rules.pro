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

# Keep other app classes that may be referenced via manifest or reflection
-keep class com.bysel.trader.utils.** { *; }
-keep class com.bysel.trader.security.** { *; }
-keep class com.bysel.trader.alerts.** { *; }
-keep class com.bysel.trader.data.auth.** { *; }
-keep class com.bysel.trader.data.local.** { *; }
-keep class com.bysel.trader.data.repository.** { *; }
-keep class com.bysel.trader.viewmodel.** { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
