# Используем ARM-совместимый образ
FROM arm64v8/eclipse-temurin:17-jdk-jammy

# Устанавливаем зависимости для Firefox
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    firefox-esr \
    xvfb \
    dbus-x11 \
    fonts-noto-cjk \
    fluxbox \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем Geckodriver вручную
RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.34.0/geckodriver-v0.34.0-linux-arm64.tar.gz \
    && tar -xzf geckodriver-*.tar.gz \
    && mv geckodriver /usr/local/bin/ \
    && chmod +x /usr/local/bin/geckodriver \
    && rm geckodriver-*.tar.gz

WORKDIR /app
COPY build/libs/thocc-project-backend-all.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]