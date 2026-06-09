# Keep entry points required by AndroidManifest
-keep class com.hermes.devicepill.MainActivity { *; }
-keep class com.hermes.devicepill.DeviceMonitorService { *; }
-keep class com.hermes.devicepill.BootReceiver { *; }

# Keep Compose runtime
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Kotlin reflect (needed by Compose)
-keep class kotlin.reflect.** { *; }

# Keep BatteryManager access
-keepclassmembers class com.hermes.devicepill.DeviceMonitorService {
    *** buildNotification(...);
}

# Strip debug info for smaller APK
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
