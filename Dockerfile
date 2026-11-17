# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Stage 1 - compile the dependency-free Java service
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /src
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources

RUN find src/main/java -name '*.java' > sources.txt \
    && mkdir -p /out \
    && javac --release 17 -encoding UTF-8 -d /out @sources.txt \
    && cp -R src/main/resources/. /out/ \
    && jar --create --file /app.jar --main-class com.arithmetic.App -C /out .

# ---------------------------------------------------------------------------
# Stage 2 - minimal, non-root runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="arithmetic-operations" \
      org.opencontainers.image.description="Arithmetic operations console - Java, Docker and Kubernetes lab" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app
COPY --from=build /app.jar /app/app.jar

ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.io.tmpdir=/tmp"

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
