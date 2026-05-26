# Keep Pdfium/AndroidPdfViewer classes
-keep class com.shockwave.** { *; }
-keep class com.github.barteksc.** { *; }

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
