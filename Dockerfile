# Stage 1: Сборка с использованием Gradle 8.4
FROM gradle:8.4.0-jdk17 AS build

# Альтернативный способ настройки DNS (без модификации resolv.conf)
RUN mkdir -p /etc/resolvconf/resolv.conf.d && \
    echo "nameserver 8.8.8.8" > /etc/resolvconf/resolv.conf.d/base && \
    echo "nameserver 1.1.1.1" >> /etc/resolvconf/resolv.conf.d/base

WORKDIR /home/gradle/project
COPY . .

# Сборка проекта с явным указанием DNS
RUN ./gradlew clean build --no-daemon --stacktrace \
    -Dorg.gradle.jvmargs='-Xmx2048m' \
    -Djava.net.preferIPv4Stack=true

# Stage 2: Финальный образ
FROM eclipse-temurin:17-jre-jammy

# Установка Firefox и geckodriver через пакетные менеджеры
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    firefox-esr \
    wget \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Установка geckodriver через официальный пакет
RUN wget -q "https://github.com/mozilla/geckodriver/releases/download/v0.33.0/geckodriver-v0.33.0-linux-aarch64.tar.gz" -O /tmp/geckodriver.tar.gz \
    tar -C /usr/local/bin -xzf /tmp/geckodriver.tar.gz && \
    chmod +x /usr/local/bin/geckodriver && \
    rm /tmp/geckodriver.tar.gz

WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]