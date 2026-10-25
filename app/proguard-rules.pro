# kotlinx.serialization: keep generated serializers for bundled content models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.pmbible.app.data.content.** {
    *** Companion;
}
-keepclasseswithmembers class com.pmbible.app.data.content.** {
    kotlinx.serialization.KSerializer serializer(...);
}
