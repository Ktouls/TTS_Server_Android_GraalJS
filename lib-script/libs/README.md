# GraalVM JAR 文件说明

此目录需要手动放置 GraalVM 20.3.13 的 jar 文件：

## 需要下载的文件

1. **polyglot-20.3.13.jar**
   - 下载地址: https://github.com/oracle/graal/releases/tag/vm-20.3.13
   - 或从 Maven Central: https://repo1.maven.org/maven2/org/graalvm/polyglot/polyglot/20.3.13/polyglot-20.3.13.jar

2. **js-20.3.13.jar**
   - 下载地址: https://github.com/oracle/graal/releases/tag/vm-20.3.13
   - 或从 Maven Central: https://repo1.maven.org/maven2/org/graalvm/polyglot/js/20.3.13/js-20.3.13.jar

## 下载命令

```bash
cd /workspace/lib-script/libs
curl -O https://repo1.maven.org/maven2/org/graalvm/polyglot/polyglot/20.3.13/polyglot-20.3.13.jar
curl -O https://repo1.maven.org/maven2/org/graalvm/polyglot/js/20.3.13/js-20.3.13.jar
```

## 注意事项

- 这两个文件不会被 Git 跟踪（已在 .gitignore 中排除）
- 需要手动下载或通过构建脚本自动下载
