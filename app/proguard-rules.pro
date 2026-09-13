# 🔥 KEEP MODEL CLASSES
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.osis.smkn1malteng.absensilate.data.** { *; }
-keep class com.osis.smkn1malteng.absensilate.data.local.** { *; }
-keep class com.osis.smkn1malteng.absensilate.data.model.** { *; }

# 🔥 KEEP ROOM
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# 🔥 KEEP GSON
-keep class com.google.gson.** { *; }
-keep class com.osis.smkn1malteng.absensilate.sync.** { *; }

# 🔥 KEEP QR CODE
-keep class com.google.zxing.** { *; }

# 🔥 KEEP COMPOSE
-keep class androidx.compose.** { *; }
-keep class androidx.compose.material3.** { *; }

# 🔥 REMOVE LOGGING (RELEASE)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 🔥 OPTIMIZATION FLAGS
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# 🔥 KEEP ML KIT CODE SCANNER
-keep class com.google.mlkit.vision.codescanner.** { *; }
-keep class com.google.android.gms.code.scanner.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
# ============================================
# 🔥 NEW PROGUARD RULES — NanoHTTPD & Network
# ============================================

# Keep NanoHTTPD classes
-keep class org.nanohttpd.** { *; }
-keepclassmembers class org.nanohttpd.** { *; }

# Keep network classes
-keep class com.osis.smkn1malteng.absensilate.network.** { *; }
-keepclassmembers class com.osis.smkn1malteng.absensilate.network.** { *; }

# Keep CompactSerializer
-keep class com.osis.smkn1malteng.absensilate.sync.CompactSerializer { *; }
-keepclassmembers class com.osis.smkn1malteng.absensilate.sync.CompactSerializer { *; }

# Keep GZIP compression classes
-keep class java.util.zip.** { *; }
-keepclassmembers class java.util.zip.** { *; }

# Keep Wi-Fi related classes
-keep class android.net.wifi.** { *; }
-keepclassmembers class android.net.wifi.** { *; }
-keep class android.net.ConnectivityManager { *; }
-keepclassmembers class android.net.ConnectivityManager { *; }
