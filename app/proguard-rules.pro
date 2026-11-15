# MultiTetris ProGuard / R8 Rules

# Keep annotations, generic signatures, and inner classes for serialization & Firebase
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all WebRTC signaling & gameplay packets (kotlinx.serialization)
-keep class com.multitetris.network.webrtc.** { *; }
-keep class com.multitetris.game.** { *; }

# Keep Firebase Realtime Database & Social models
-keep class com.multitetris.network.social.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# Keep DataStore persisted models (Career Stats, Economy, Daily Missions)
-keep class com.multitetris.stats.** { *; }
-keep class com.multitetris.economy.** { *; }

# Keep kotlinx.serialization generated serializers and companions
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Keep org.webrtc JNI & native callback classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
