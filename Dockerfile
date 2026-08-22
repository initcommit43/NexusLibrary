# syntax=docker/dockerfile:1

# --- Stage 1: the single-page app -------------------------------------------
# Built first and copied into the backend's static resources, so one image and
# one origin serve both. See WebConfig for why the origins are not split.
FROM node:24-alpine AS frontend
WORKDIR /build

# Dependencies are their own layer: package files change far less often than
# source, so editing a component does not reinstall node_modules.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build


# --- Stage 2: the Spring Boot jar -------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline

COPY backend/src ./src
COPY --from=frontend /build/dist ./src/main/resources/static
# Tests need Docker for Testcontainers, which is unavailable inside a build.
# They gate the phase in CI instead.
RUN mvn -B clean package -DskipTests

# Split the jar so dependencies and application code land in separate image
# layers. Dependencies rarely change, so a code-only rebuild reuses them.
RUN java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination extracted


# --- Stage 3: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Unprivileged: a container process should not be able to write its own image
# or reach the host as root if the app is ever compromised.
RUN addgroup -S nexus && adduser -S nexus -G nexus
WORKDIR /app

COPY --from=backend --chown=nexus:nexus /build/extracted/dependencies/ ./
COPY --from=backend --chown=nexus:nexus /build/extracted/spring-boot-loader/ ./
COPY --from=backend --chown=nexus:nexus /build/extracted/snapshot-dependencies/ ./
COPY --from=backend --chown=nexus:nexus /build/extracted/application/ ./

USER nexus
EXPOSE 8080

# MaxRAMPercentage keeps the JVM inside the container's memory limit rather
# than sizing the heap from the host's total RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

# --launcher extracts an exploded layout rather than a runnable jar, so the app
# starts through Spring Boot's launcher class instead of -jar.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
