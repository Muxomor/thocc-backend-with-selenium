FROM openjdk:17
RUN mkdir /app
COPY ./build/libs/thocc-project-backend-all.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]