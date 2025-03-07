# Stage 1: Сборка приложения с помощью Gradle
FROM gradle:8.2.1-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle clean build --no-daemon

# Stage 2: Запуск приложения на базе Debian (sid)
FROM debian:sid
ENV DEBIAN_FRONTEND=noninteractive

# Обновляем пакеты и устанавливаем openjdk-17 и firefox-esr
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    firefox-esr \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Скачиваем и устанавливаем geckodriver для arm64 (укажите актуальную версию)
RUN wget -O /tmp/geckodriver.tar.gz https://github.com/mozilla/geckodriver/releases/download/v0.33.0/geckodriver-v0.33.0-linux-aarch64.tar.gz \
    && tar -C /usr/local/bin -xzvf /tmp/geckodriver.tar.gz \
    && chmod +x /usr/local/bin/geckodriver \
    && rm /tmp/geckodriver.tar.gz

WORKDIR /app
# Копируем fat jar, содержащий все зависимости
COPY --from=build /home/gradle/project/build/libs/thocc-project-backend-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]