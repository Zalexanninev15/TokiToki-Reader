# Retrofit interfaces are reflective; keep their signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keep,allowobfuscation interface <1>
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }

# kotlinx.serialization generated serializers.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }

# Never let an obfuscated build leak tokens through a stack trace of a data class.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
