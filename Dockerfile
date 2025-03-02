FROM gradle:7.6-jdk17-alpine as builder

WORKDIR /app
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradlew .
COPY gradle gradle
COPY src src

RUN ./gradlew build --no-daemon

FROM openjdk:17-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/thocc-project-backend-all.jar .

EXPOSE 7895
CMD ["java", "-jar", "thocc-project-backend-all.jar"]