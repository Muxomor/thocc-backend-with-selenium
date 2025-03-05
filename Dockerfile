FROM openjdk:17
RUN apk add --no-cache \
    firefox-esr \
    xvfb \
    dbus \
    ttf-freefont \
    fluxbox \
    mesa-dri-swrast \
    geckodriver \
    udev

RUN mkdir /app
COPY ./build/libs/thocc-project-backend-all.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]