FROM eclipse-temurin:17-jdk-jammy

# Устанавливаем зависимости для Firefox
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    firefox \
    xvfb \
    dbus \
    fonts-freefont-ttf \
    fluxbox \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    geckodriver && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir /app
COPY ./build/libs/thocc-project-backend-all.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]