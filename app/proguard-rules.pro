# Keep ATAK plugin lifecycle classes
-keep class com.atakmap.android.scythe.plugin.ScytheLifecycle { *; }
-keep class com.atakmap.android.scythe.ScytheMapComponent { *; }
-keep class com.atakmap.android.scythe.ScytheDropDownReceiver { *; }

# Keep model DTOs (used reflectively by JSON parsers)
-keep class com.atakmap.android.scythe.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# org.json
-keep class org.json.** { *; }
