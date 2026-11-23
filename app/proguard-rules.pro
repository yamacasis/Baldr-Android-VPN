# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Preserve Room entities, DAOs, and Kotlin Serialization routes/models
-keep class com.example.model.** { *; }
-keep class com.example.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.example.data.** { *; }

# Preserve Firebase messaging service & Crashlytics breadcrumbs
-keep class com.example.vpn.AppFirebaseMessagingService { *; }
-keep class com.example.vpn.CrashReporter { *; }
-keep class com.example.vpn.AppAnalytics { *; }
-keep class com.example.vpn.RemoteUpdateManager { *; }
