# CNB 开发环境 Dockerfile
# 基于 Android 构建环境

# 使用官方 OpenJDK 17 基础镜像（Android 8.8.1 需要 JDK 17+）
FROM openjdk:17-jdk-slim

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

# 安装基础依赖
RUN apt-get update && apt-get install -y \
    git \
    wget \
    unzip \
    curl \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# 下载并安装 Android SDK Command Line Tools 并接受许可证
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-11076708_latest.zip && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest && \
    yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-35" \
        "platforms;android-21" \
        "build-tools;35.0.0" \
        "extras;android;m2repository" \
        "extras;google;m2repository"

# 设置工作目录
WORKDIR /workspace

# 复制必要的项目文件
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY gradle.properties ./
COPY build.gradle ./
COPY settings.gradle ./
COPY libs.versions.toml ./
COPY build-logic ./build-logic
COPY local.properties ./
COPY app ./app

# 复制并解码 keystore 文件（用于签名）
COPY keystore.base64 ./
RUN base64 -d keystore.base64 > app/keystore.jks && rm keystore.base64

# 授予 Gradle Wrapper 执行权限
RUN chmod +x gradlew

# 预先下载 Gradle Wrapper（加速首次构建）
RUN ./gradlew --version

# 复制其他必要的库模块
COPY lib-common ./lib-common
COPY lib-compose ./lib-compose
COPY lib-database ./lib-database
COPY lib-script ./lib-script
COPY lib-server ./lib-server
COPY lib-tts ./lib-tts

# 暴露常用端口（如 Ktor 服务器端口）
EXPOSE 8080

# 默认命令
CMD ["/bin/bash"]
