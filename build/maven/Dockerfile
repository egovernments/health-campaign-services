# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
ARG WORK_DIR
WORKDIR /app

# Copy POM, entrypoint script, and source
COPY ${WORK_DIR}/pom.xml       ./pom.xml
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src           ./src

# Build the app (skip tests for speed)
RUN mvn -B -f pom.xml package


# ─────────────── RUNTIME STAGE ───────────────
FROM openjdk:8-jdk-alpine AS runtime
WORKDIR /opt/egov

# Pull in the fat JAR and our startup script
COPY --from=build /app/target/*.jar ./application.jar
COPY --from=build /app/start.sh    ./start.sh

RUN chmod +x ./start.sh

ENTRYPOINT ["sh", "./start.sh"]
