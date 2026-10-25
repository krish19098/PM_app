# kotlinx.serialization: keep generated serializers for bundled content models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.pmbible.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.pmbible.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
