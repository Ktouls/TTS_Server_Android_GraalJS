# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-keepattributes SourceFile,LineNumberTable

# GraalVM Polyglot - suppress JVMCI warnings
-dontwarn jdk.vm.ci.**
-dontwarn com.oracle.truffle.api.**
-dontwarn com.oracle.truffle.runtime.**

# Keep GraalVM related classes used by TTS plugin engine
-keep class org.graalvm.polyglot.** { *; }
-keep class org.graalvm.polyglot.PolyglotException { *; }

# Keep TTS plugin engine classes
-keep class com.github.jing332.tts.speech.plugin.engine.** { *; }

# Keep plugin interfaces
-keep class * implements com.github.jing332.tts.speech.plugin.engine.JsBridgeInputStream$Callback { *; }
