package java.lang;

/**
 * 模拟的 java.lang.Module 类，用于 GraalVM Android 兼容性
 * 
 * 注意：这个类是为了让 GraalVM 23.1.2 在 Android 上运行而创建的模拟类。
 * GraalVM 在 Android 上会尝试调用 Class.getModule()，但 Android 不存在这个类。
 * 
 * Android API 26+ 使用 Java 8 语言特性，不包括 Java 9+ 的模块系统。
 * 这个类提供了一个最小化的实现来避免 NoSuchMethodError。
 */
public final class Module {
    private final String name;
    private final ClassLoader classLoader;

    Module(String name, ClassLoader classLoader) {
        this.name = name;
        this.classLoader = classLoader;
    }

    public String getName() {
        return name;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public boolean isNamed() {
        return true;
    }

    public boolean isOpen() {
        return true;
    }

    public boolean isExported(String pn) {
        return true;
    }

    public boolean isExported(String pn, Module other) {
        return true;
    }

    public boolean isOpen(String pn) {
        return true;
    }

    public boolean isOpen(String pn, Module other) {
        return true;
    }

    public boolean canRead(Module other) {
        return true;
    }

    public void addReads(Module other) {
    }

    public void addExports(String pn, Module other) {
    }

    public void addOpens(String pn, Module other) {
    }

    public boolean isNative(Module other) {
        return false;
    }

    public String toString() {
        return name;
    }
}
