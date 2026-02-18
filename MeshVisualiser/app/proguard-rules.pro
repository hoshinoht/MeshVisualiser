# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ARCore
-keep class com.google.ar.** { *; }

# SceneView
-keep class io.github.sceneview.** { *; }

# Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
