# Stage 1: Сборка приложения
FROM gradle:8.4.0-jdk17 AS build

WORKDIR /home/gradle/project

# Копирование Gradle Wrapper и установка прав
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# Копирование остальных файлов проекта
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# Проверка структуры проекта и версии gradlew
RUN ls -la && \
    ./gradlew --version

# Сборка проекта
RUN ./gradlew clean build --no-daemon --stacktrace

# Stage 2: Финальный образ для ARM64
FROM debian:sid-slim

# Настройка источников и установка необходимых пакетов
RUN echo "deb http://deb.debian.org/debian sid main non-free contrib" > /etc/apt/sources.list && \
    apt-get update -o Acquire::Check-Valid-Until=false && \
    apt-get install -y --no-install-recommends \
        firefox-esr \
        openjdk-17-jre-headless \
        wget \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Установка geckodriver для ARM64
RUN wget -O /tmp/geckodriver.tar.gz \
    https://github.com/mozilla/geckodriver/releases/download/v0.33.0/geckodriver-v0.33.0-linux-aarch64.tar.gz && \
    tar -C /usr/local/bin -xzf /tmp/geckodriver.tar.gz && \
    chmod +x /usr/local/bin/geckodriver && \
    rm /tmp/geckodriver.tar.gz

WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]
