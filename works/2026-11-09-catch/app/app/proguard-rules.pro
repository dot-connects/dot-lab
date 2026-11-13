# Shrink only; names stay (crash logs readable, no reflection surprises).
-dontobfuscate
# MediaPipe tasks call back into Java from native code and parse protobufs by reflection: keep them whole.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.common.flogger.** { *; }
-keep class com.google.ar.** { *; }
-ignorewarnings
