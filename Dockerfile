FROM gradle:8.4.0-jdk17 AS build

ENV TZ=Europe/Moscow
RUN apt-get update && apt-get install -y tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

WORKDIR /home/gradle/project

COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

RUN ls -la && \
    ./gradlew --version

RUN ./gradlew clean build --no-daemon --stacktrace

FROM debian:sid-slim

RUN echo "deb http://deb.debian.org/debian sid main non-free contrib" > /etc/apt/sources.list && \
    apt-get update -o Acquire::Check-Valid-Until=false && \
    apt-get install -y --no-install-recommends \
        firefox-esr \
        openjdk-17-jre-headless \
        wget \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN wget -O /tmp/geckodriver.tar.gz \
    https://github.com/mozilla/geckodriver/releases/download/v0.33.0/geckodriver-v0.33.0-linux-aarch64.tar.gz && \
    tar -C /usr/local/bin -xzf /tmp/geckodriver.tar.gz && \
    chmod +x /usr/local/bin/geckodriver && \
    rm /tmp/geckodriver.tar.gz

WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*-all.jar app.jar

EXPOSE 7895
CMD ["java", "-jar", "app.jar"]
