# Strip verbose/debug logging calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve serialization metadata and annotations for R8 full mode
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
