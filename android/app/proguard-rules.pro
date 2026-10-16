# kotlinx.serialization, OkHttp, Glance and WorkManager ship their own consumer rules.
# Keep @Serializable model classes' generated serializers (belt and braces for R8 full mode).
-keepclassmembers @kotlinx.serialization.Serializable class net.jwowk.clickupwidget.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class net.jwowk.clickupwidget.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# WorkManager instantiates workers reflectively by class name.
-keep class net.jwowk.clickupwidget.work.** extends androidx.work.ListenableWorker { <init>(...); }
# Glance instantiates ActionCallbacks reflectively by class name (actionRunCallback<T>()); keep their
# no-arg constructors or every widget tap fails in release with NoSuchMethodException.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
