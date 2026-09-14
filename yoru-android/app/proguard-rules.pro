-allowaccessmodification
-renamesourcefileattribute Yoru
-assumenosideeffects class android.util.Log {
 public static *** d(...);
 public static *** v(...);
 public static *** i(...);
 public static *** w(...);
 public static *** e(...);
}
-keepclassmembers class * {
 @android.webkit.JavascriptInterface <methods>;
}

-keep class go.** { *; }

-keep class app.yoru.mobile.DocumentExportWorker {
 public <init>(android.content.Context, androidx.work.WorkerParameters);
}
