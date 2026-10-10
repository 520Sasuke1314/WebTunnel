# ---- JSch（Pinggy SSH 隧道）----
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**

# ---- NanoHTTPD（本地 HTTP 服务）----
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ---- ZXing（二维码）----
-keep class com.google.zxing.** { *; }

# 保留行号，方便排查线上问题
-keepattributes SourceFile,LineNumberTable
