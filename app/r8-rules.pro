# R8 配置：忽略 GraalVM 使用的 Java 9+ 类
# 这些类在 Android 上不存在，但 GraalVM 的引用在运行时不会被使用

# 忽略缺失的 Module 相关类
-dontwarn java.lang.Module
-dontwarn java.lang.module.**
-dontwarn java.lang.invoke.MethodHandle
-dontwarn java.lang.invoke.MethodHandles
-dontwarn java.lang.invoke.VarHandle
-dontwarn java.lang.invoke.CallSite
-dontwarn java.lang.invoke.ConstantCallSite

# 忽略其他缺失的 Java API 类
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.script.**
-dontwarn kotlinx.coroutines.slf4j.MDCContext

# 忽略可选依赖的类
-dontwarn coil3.PlatformContext
-dontwarn com.drake.brv.PageRefreshLayout
-dontwarn com.drake.statelayout.StateLayout

# 假设 Class.getModule() 存在并返回 null，避免 NoSuchMethodError
# 这是 GraalVM 在 Android 上运行的关键修复
-assumenosideeffects class java.lang.Class {
    public java.lang.Module getModule();
}

# 假设 Module 相关方法存在
-assumenosideeffects class java.lang.Module {
    *** *(...);
}

# 保留所有引用这些类的代码
-keep class * {
    *** getModule(...);
    *** getDescriptor(...);
    *** invokeExact(...);
    *** invokeWithArguments(...);
}

# 不删除对这些类的引用
-keepclassmembers class * {
    *** *(java.lang.Module);
    *** *(java.lang.invoke.MethodHandle);
    *** *(java.lang.invoke.MethodHandles);
    *** *(java.lang.invoke.VarHandle);
    *** *(java.lang.invoke.CallSite);
    *** *(java.lang.reflect.AnnotatedType);
    *** *(javax.script.ScriptEngine);
    *** *(javax.script.ScriptEngineManager);
    *** *(kotlinx.coroutines.slf4j.MDCContext);
}
