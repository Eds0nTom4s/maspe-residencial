FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/sistema-restauracao-1.0.0.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
