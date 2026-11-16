# Metode yang dipanggil JavaScript halaman ujian lewat addJavascriptInterface
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
