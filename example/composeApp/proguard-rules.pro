# Keep JNA classes
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Cactus library classes
-keep class com.cactus.** { *; }
-dontwarn com.cactus.** 