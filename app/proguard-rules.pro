# H9 Qibla 5.8 production hardening
-keepnames class sa.h9.qibla.MainActivity
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
