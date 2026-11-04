# Retrofit / OkHttp / Gson - keep model classes used for JSON (de)serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.leakguard.ai.data.remote.dto.** { *; }
-keep class com.leakguard.ai.domain.model.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# androidx.security:security-crypto pulls in Google Tink. Without these,
# minifyEnabled release builds can throw ClassNotFoundException /
# GeneralSecurityException from inside EncryptedSharedPreferences the first
# time SessionManager is constructed - i.e. login "works" in debug and then
# hard-crashes on first launch of a release build.
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.subtle.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-keep class androidx.security.crypto.** { *; }
