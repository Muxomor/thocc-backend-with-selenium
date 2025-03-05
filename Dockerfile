FROM arm64v8/ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive

# Устанавливаем зависимости: OpenJDK 17, Firefox, Xvfb и необходимые библиотеки
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        ca-certificates \
        firefox \
        xvfb \
        dbus-x11 \
        fonts-noto-core \
        libgl1-mesa-dri \
        libgl1-mesa-glx \
        libxt6 && \
    rm -rf /var/lib/apt/lists/*

# Скачиваем и устанавливаем Geckodriver для ARM64
RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.34.0/geckodriver-v0.34.0-linux-arm64.tar.gz && \
    tar -xzf geckodriver-v0.34.0-linux-arm64.tar.gz && \
    mv geckodriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/geckodriver && \
    rm geckodriver-v0.34.0-linux-arm64.tar.gz

WORKDIR /app
COPY build/libs/thocc-project-backend-all.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
