# Stage 1: Сборка с использованием Gradle 8.4
FROM gradle:8.4.0-jdk17 AS build

# Фикс DNS для контейнера
RUN echo "nameserver 8.8.8.8" | tee /etc/resolv.conf && \
    echo "nameserver 1.1.1.1" | tee -a /etc/resolv.conf

WORKDIR /home/gradle/project

# Копируем файлы сборки с кэшированием
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# Проверка структуры
RUN ls -la && \
    ls -l build.gradle.kts && \
    ./gradlew --version

# Сборка проекта (исправлен синтаксис многострочной команды)
RUN ./gradlew clean build --no-daemon --stacktrace --info \
    --refresh-dependencies \
    -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=512m'

# Stage 2: Финальный образ
FROM debian:sid
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    firefox-esr \
    wget \
    && rm -rf /var/lib/apt/lists/*

RUN wget -O /tmp/geckodriver.tar.gz https://github.com/mozilla/geckodriver/releases/download/v0.33.0/geckodriver-v0.33.0-linux-aarch64.tar.gz \
    && tar -C /usr/local/bin -xzvf /tmp/geckodriver.tar.gz \
    && chmod +x /usr/local/bin/geckodriver \
    && rm /tmp/geckodriver.tar.gz

WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/thocc-project-backend-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]