# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep model classes
-keep class com.billbook.app.model.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Apache POI / xmlbeans / log4j / saxon (optional deps not available on Android)
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.**
