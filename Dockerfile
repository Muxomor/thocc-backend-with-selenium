FROM arm64v8/ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive
ENV DISPLAY=:99
ENV MOZ_ENABLE_WAYLAND=0

# Устанавливаем зависимости: OpenJDK, Firefox, Xvfb, wget, tar и прочее
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
        libxt6 \
        wget \
        tar && \
    rm -rf /var/lib/apt/lists/*

# Скачиваем и устанавливаем Geckodriver для ARM64 (правильное имя файла)
RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.34.0/geckodriver-v0.34.0-linux-aarch64.tar.gz && \
    tar -xzf geckodriver-v0.34.0-linux-aarch64.tar.gz && \
    mv geckodriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/geckodriver && \
    rm geckodriver-v0.34.0-linux-aarch64.tar.gz

WORKDIR /app
COPY build/libs/thocc-project-backend-all.jar app.jar

# Копируем скрипт запуска
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
