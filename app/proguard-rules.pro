# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep model classes
-keep class com.billbook.app.model.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
