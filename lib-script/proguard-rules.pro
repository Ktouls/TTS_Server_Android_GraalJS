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
#-renamesourcefileattribute SourceFile

# GraalVM Polyglot and Truffle
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep all GraalVM Polyglot classes and members
-keep class org.graalvm.polyglot.** { *; }
-keep class com.oracle.truffle.** { *; }

# Keep GraalVM polyglot API classes
-keep class org.graalvm.polyglot.Context { *; }
-keep class org.graalvm.polyglot.Engine { *; }
-keep class org.graalvm.polyglot.Source { *; }
-keep class org.graalvm.polyglot.Value { *; }

# Keep GraalVM Value class methods needed for runtime
-keepclassmembers class org.graalvm.polyglot.Value {
    public boolean hasMembers();
    public boolean hasArrayElements();
    public java.lang.String[] memberKeys();
    public org.graalvm.polyglot.Value getMember(java.lang.String);
    public long getArraySize();
    public org.graalvm.polyglot.Value getArrayElement(long);
    public java.lang.String asString();
    public int asInt();
    public long asLong();
    public double asDouble();
    public boolean asBoolean();
    public byte asByte();
    public boolean isNull();
    public boolean isString();
    public boolean isNumber();
    public boolean isBoolean();
    public boolean isHostObject();
    public boolean canExecute();
}

# Keep GraalVM proxy classes
-keep class org.graalvm.polyglot.proxy.** { *; }

# Suppress warnings for missing GraalVM JVMCI classes (not used on Android)
-dontwarn jdk.vm.ci.**
-dontwarn com.oracle.truffle.api.**
-dontwarn com.oracle.truffle.runtime.**
-dontwarn jdk.internal.misc.**

# Android-specific: Suppress warnings for Java 9+ module system
-dontwarn java.lang.module.**
-dontwarn java.lang.Class
-dontwarn java.lang.Module
-dontwarn java.lang.invoke.**

# Keep all GraalVM JS engine classes
-keep class org.graalvm.js.** { *; }

# Keep all script runtime classes
-keep class com.github.jing332.script.** { *; }

# Keep ScriptEngine interface and implementations
-keep class * extends com.github.jing332.script.ScriptEngine { *; }

# Keep script runtime classes
-keep class com.github.jing332.script.runtime.** { *; }

# Keep script extensions
-keep class com.github.jing332.script.simple.ext.** { *; }

# Keep JS buffer class
-keep class com.github.jing332.script.runtime.GraalJSBuffer { *; }