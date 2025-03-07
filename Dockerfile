# Stage 1: Сборка приложения с помощью Gradle
FROM gradle:8.2.1-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
RUN ls -l
RUN ls -l build.gradle
RUN gradle clean build --no-daemon --stacktrace

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