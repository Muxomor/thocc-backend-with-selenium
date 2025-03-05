#!/bin/sh
# Запускаем Xvfb в фоне
Xvfb :99 -screen 0 1024x768x16 &
# Запускаем Java-приложение
exec java -jar app.jar
