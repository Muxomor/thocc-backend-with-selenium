# Stage 1: Сборка приложения с помощью Gradle
FROM gradle:8.2.1-jdk17 AS build
WORKDIR /home/gradle/project
# Копируем исходный код в контейнер
COPY . .
# Сборка проекта; fat jar обычно генерируется задачей shadowJar
RUN gradle clean build --no-daemon

# Stage 2: Запуск приложения
FROM openjdk:17-slim
WORKDIR /app

# Обновляем пакеты и устанавливаем Firefox ESR и firefox-geckodriver для arm64
RUN apt-get update && apt-get install -y \
    firefox-esr \
    firefox-geckodriver \
    && rm -rf /var/lib/apt/lists/*

# Копируем fat jar из предыдущего этапа
COPY --from=build /home/gradle/project/build/libs/thocc-project-backend-all.jar app.jar

# Открываем порт, на котором будет работать приложение
EXPOSE 7895

# Запуск приложения
CMD ["java", "-jar", "app.jar"]