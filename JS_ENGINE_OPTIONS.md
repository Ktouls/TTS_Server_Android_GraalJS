# Android JavaScript 引擎支持 ES6 的选项

## 问题总结

当前项目从 Rhino 切换到 GraalVM 23.1.2，遇到以下问题：
- ❌ GraalVM 23.1.2 在 Android 上存在根本性兼容性问题
- ❌ 内部调用 Java 9+ 模块系统 API（`Class.getModule()`），Android 不存在
- ❌ Maven 上没有 22.3.2 版本

## 可行的 JavaScript 引擎方案

### 1. Rhino + Babel（推荐用于短期）
**优点：**
- ✅ Rhino 在 Android 上运行稳定
- ✅ 使用 Babel 将 ES6 转换为 ES5
- ✅ 不改变太多现有代码
- ✅ 立即可用

**缺点：**
- ❌ 需要运行时转换，性能开销
- ❌ 不原生支持 ES6 特性

**实现方式：**
```kotlin
// 使用 Babel Standalone 转换 ES6 代码
val babelCode = """
  const babel = require('https://unpkg.com/@babel/standalone@7.23.0/babel.min.js');
  const transformed = babel.transform(code, { presets: ['@babel/preset-env'] });
  return transformed.code;
"""
val es5Code = RhinoEngine.eval(babelCode)
RhinoEngine.eval(es5Code)
```

### 2. QuickJS-Android（推荐用于长期）
**优点：**
- ✅ 原生支持 ES6+
- ✅ 轻量级（~400KB）
- ✅ 性能优秀
- ✅ Android 兼容性好

**缺点：**
- ❌ 需要集成 JNI
- ❌ Maven 上可能没有现成的库
- ❌ 需要手动编译或从 GitHub 集成

**GitHub 项目：**
- https://github.com/whiler/quickjs-android

**集成方式：**
1. 添加 Gradle 依赖：
```kotlin
implementation("com.github.whiler:quickjs-android:1.0.0")
```

2. 修改代码以使用 QuickJS API

### 3. GraalVM 21.3.x（需要验证）
**优点：**
- ✅ 支持 ES6+
- ✅ 已有代码基础

**缺点：**
- ❌ 可能仍有 Android 兼容性问题
- ❌ Maven 上没有 21.x 版本
- ❌ 需要手动构建或寻找第三方仓库

### 4. JavaScriptCore（仅 iOS，不可用）
- ❌ iOS 原生引擎，Android 不支持

### 5. V8 for Android（复杂）
**优点：**
- ✅ 支持 ES6+
- ✅ Google 官方引擎

**缺点：**
- ❌ 集成极其复杂
- ❌ 体积大（~20MB+）
- ❌ 需要大量 JNI 绑定

## 推荐方案

### 方案 A：QuickJS-Android（最佳平衡）
1. Fork 或 clone quickjs-android 项目
2. 将其集成到项目中
3. 修改 `ScriptEngine` 接口以适配 QuickJS
4. 测试所有插件

**时间估算：** 2-3 天开发 + 1-2 天测试

### 方案 B：Rhino + Babel（快速回退）
1. 回到 Rhino 引擎
2. 集成 Babel Standalone 转换
3. 在脚本加载时自动转换 ES6 为 ES5
4. 缓存转换后的代码

**时间估算：** 半天开发 + 半天测试

### 方案 C：继续尝试 GraalVM（风险高）
1. 尝试 GraalVM 23.0.0（当前尝试中）
2. 如果失败，考虑使用 GraalVM Native Image 编译 Android 专用版本
3. 使用 ProGuard/R8 规则处理缺失的类

**时间估算：** 不确定，可能需要数天调试

## 当前状态

- ✅ 正在尝试 GraalVM 23.0.0（23 系列的第一个版本）
- ⏳ 等待构建结果
- ⚠️ 如果仍失败，建议采用方案 A（QuickJS）或方案 B（Rhino + Babel）

## 建议

**如果 GraalVM 23.0.0 仍失败，我建议：**

1. **短期方案（1-2天）：** 使用 Rhino + Babel
   - 快速回退到可工作状态
   - 保留 GraalVM 代码以便将来使用

2. **长期方案（1-2周）：** 集成 QuickJS-Android
   - 获得原生的 ES6 支持
   - 更好的性能和更小的体积

**请选择你偏好的方案，我可以协助实施。**
