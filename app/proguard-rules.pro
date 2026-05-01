-keep class androidx.security.crypto.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*, InnerClasses
-dontwarn androidx.security.crypto.**
