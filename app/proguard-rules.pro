# Add project specific ProGuard rules here.
# https://developer.android.com/studio/build/shrink-code

# Keep the JavaScript bridge class and its methods: R8 must not rename/strip
# anything called from the WebView via @JavascriptInterface, or the game's
# native bridge calls (AndroidBridge.xxx) will silently stop working in release builds.
-keepclassmembers class com.nestordev.astrosiege.GameBridge {
    public *;
}
-keep class com.nestordev.astrosiege.GameBridge { *; }
