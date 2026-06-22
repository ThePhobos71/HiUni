# =================================================================
# HiUni — R8/ProGuard Rules
# =================================================================
# Behalte Line-Numbers für lesbare Stack Traces in Crash Reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =================================================================
# Hilt / Dagger
# =================================================================
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keepclassmembers class **_HiltModules$KeyModule { *; }
-keepnames @dagger.hilt.android.HiltAndroidApp class *
-keepnames @dagger.hilt.android.AndroidEntryPoint class *

# =================================================================
# Room
# =================================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# =================================================================
# kotlinx.serialization
# =================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class de.transio.hiuni.**$$serializer { *; }
-keepclassmembers class de.transio.hiuni.** {
    *** Companion;
}
-keepclasseswithmembers class de.transio.hiuni.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class de.transio.hiuni.** { *; }

# =================================================================
# Coroutines
# =================================================================
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# =================================================================
# OkHttp / Okio
# =================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# =================================================================
# Jsoup
# =================================================================
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# =================================================================
# Jakarta Mail (Angus Mail)
# =================================================================
-keep class jakarta.mail.** { *; }
-keep class org.eclipse.angus.mail.** { *; }
-dontwarn jakarta.mail.**
-dontwarn org.eclipse.angus.mail.**
-dontwarn com.sun.activation.**

# =================================================================
# Coil
# =================================================================
-dontwarn coil.**

# =================================================================
# Google Tink (transitive via androidx.security:security-crypto)
# =================================================================
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# =================================================================
# App eigene Daten-Klassen — Entities, DTOs, API-Models
# =================================================================
# Reflection auf Datenklassen vermeiden, die für Serialization
# oder Room genutzt werden — Felder erhalten.
-keep class de.transio.hiuni.feature.**.data.** { *; }
-keep class de.transio.hiuni.core.database.** { *; }
