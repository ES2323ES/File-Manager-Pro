# File Manager Pro R8 / ProGuard Configuration (v0.2.8)
-keep class com.aishhatorah.filemanagerpro.** { *; }
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.xmlpull.v1.**
-dontwarn androidx.**
