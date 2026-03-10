# Pertahankan class aplikasi
-keep class id.sch.smkn1gempol.exambro.** { *; }
-keepattributes *Annotation*

# WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Material Components
-keep class com.google.android.material.** { *; }

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
}

# Buang log di release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
