# Add project specific ProGuard rules here.

# Kotlinx Serialization — keep serializer companions and @Serializable types
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.prism.**$$serializer { *; }
-keepclassmembers class com.example.prism.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.prism.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room — keep entity constructors and DAO methods
-keep class com.example.prism.data.entity.** { *; }
-keep class com.example.prism.data.db.** { *; }

# Timber — strip debug/verbose logs in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
}
