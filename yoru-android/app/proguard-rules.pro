-allowaccessmodification
-renamesourcefileattribute Yoru
-assumenosideeffects class android.util.Log {
 public static *** d(...);
 public static *** v(...);
 public static *** i(...);
}
-keepclassmembers class * {
 @android.webkit.JavascriptInterface <methods>;
}

-keep class go.** { *; }
