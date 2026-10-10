# Proguard rules for Centium VPN

# Preserve JNI exports
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class hev.htproxy.** { *; }
-keep class org.centium.vpn.bridge.** { *; }

# Keep data models used with Gson serialization
-keepclassmembers class org.centium.vpn.data.** { *; }
-keep class org.centium.vpn.data.** { *; }

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**

# Tor: TorService is started by class name and libtor.so calls back into it via JNI
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }
