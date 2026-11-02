FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN bash ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
