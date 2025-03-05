# Используем базовый образ для ARM64
FROM arm64v8/ubuntu:24.10

# Устанавливаем OpenJDK 17
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем зависимости для Firefox и GUI
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    firefox \
    xvfb \
    dbus-x11 \
    fonts-noto-core \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    libxt6 \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем Geckodriver
RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.34.0/geckodriver-v0.34.0-linux-arm64.tar.gz \
    && tar -xzf geckodriver-*.tar.gz \
    && mv geckodriver /usr/local/bin/ \
    && chmod +x /usr/local/bin/geckodriver \
    && rm geckodriver-*.tar.gz

# Настраиваем рабочую директорию
WORKDIR /app
COPY build/libs/thocc-project-backend-all.jar app.jar

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]