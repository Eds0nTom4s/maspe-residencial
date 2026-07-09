FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=target/*.jar

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && chown app:app /app

COPY ${JAR_FILE} /app/app.jar

ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

EXPOSE 8080

USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
