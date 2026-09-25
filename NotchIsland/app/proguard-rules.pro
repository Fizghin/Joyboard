# The app's own code is a small fraction of the APK — nearly all of the shrinking comes from
# dead library code — so keeping it whole costs little and removes a whole class of release-only
# crash (something reached reflectively getting stripped or renamed). Stack traces from the field
# stay readable too.
-keep class com.joyboard.notchisland.** { *; }

# The system instantiates these by name from the manifest, so they must keep theirs.
-keep class com.joyboard.notchisland.NotchApp
-keep class com.joyboard.notchisland.MainActivity
-keep class com.joyboard.notchisland.CalibrationActivity
-keep class com.joyboard.notchisland.ShortcutRouterActivity
-keep class com.joyboard.notchisland.service.** { *; }
-keepclassmembers class * extends android.service.notification.NotificationListenerService { *; }
-keepclassmembers class * extends android.service.quicksettings.TileService { *; }

# Settings are stored and restored by enum name, so those names have to survive.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.joyboard.notchisland.data.** { *; }

# org.json is used for the update manifest and settings backups.
-dontwarn org.json.**

# Keep line numbers so a stack trace from the field is worth reading.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
