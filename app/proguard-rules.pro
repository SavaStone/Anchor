# Opora release ProGuard/R8 rules.
#
# Room: entities/DAOs are referenced by generated code; generated *_Impl classes
# are looked up through Room's generated-implementation mechanism. Keep them so
# R8 cannot rename or remove the database implementation.
-keep @androidx.room.Entity class app.opora.data.db.** { *; }
-keep @androidx.room.Dao interface app.opora.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class app.opora.data.db.**_Impl { *; }

# kotlinx.serialization: generated serializers and companion serializer()
# functions. Without these, backup/restore can fail at runtime with
# "Serializer for class ... is not found".
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.opora.**$$serializer { *; }
-keepclassmembers class app.opora.** {
    *** Companion;
}
-keepclasseswithmembers class app.opora.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp: optional TLS implementations that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# PDF report entry point: the report screen is a direct caller, but the class
# is part of the share flow and is kept stable for diagnostics and any future
# name-based lookup.
-keep class app.opora.reports.ReportGenerator { public *; }
