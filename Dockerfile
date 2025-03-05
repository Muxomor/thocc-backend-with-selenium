FROM arm64v8/eclipse-temurin:17-jdk-jammy

# Добавляем необходимые репозитории
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    software-properties-common \
    && add-apt-repository -y universe \
    && apt-get update

# Устанавливаем зависимости с явным указанием версий
RUN apt-get install -y --no-install-recommends \
    firefox-esr=115.12.0~mozillabinaries-0ubuntu0.22.04.1 \
    xvfb=2:1.20.13-1ubuntu3~22.04.2 \
    dbus-x11=1.12.20-2ubuntu4.1 \
    fonts-noto-core \
    fluxbox=1.3.7-4.1 \
    libgl1-mesa-dri:arm64 \
    libgl1-mesa-glx:arm64 \
    libxt6:arm64 \
    && rm -rf /var/lib/apt/lists/*

# Ручная установка geckodriver для ARM64
RUN wget https://github.com/mozilla/geckodriver/releases/download/v0.34.0/geckodriver-v0.34.0-linux-arm64.tar.gz \
    && tar -xzf geckodriver-*.tar.gz \
    && mv geckodriver /usr/local/bin/ \
    && chmod +x /usr/local/bin/geckodriver \
    && rm geckodriver-*.tar.gz

WORKDIR /app
COPY build/libs/thocc-project-backend-all.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]