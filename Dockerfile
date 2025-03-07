# Stage 1: сборка приложения с помощью Gradle
FROM gradle:8.2.1-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle clean build --no-daemon

# Stage 2: запуск приложения на базе Ubuntu для arm64
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

# Устанавливаем openjdk-17, Firefox ESR и firefox-geckodriver
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    firefox-esr \
    firefox-geckodriver \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# Копируем fat jar, который включает все зависимости
COPY --from=build /home/gradle/project/build/libs/thocc-project-backend-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]