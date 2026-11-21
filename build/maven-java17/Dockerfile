# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
ARG WORK_DIR
WORKDIR /app

# Copy project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY ${WORK_DIR}/src ./src

# Build the project
RUN mvn -B -f /app/pom.xml package

# ─────────────── RUNTIME STAGE ───────────────
FROM gcr.io/distroless/java17-debian12:nonroot@sha256:06484c2a9dcc9070aeafbc0fe752cb9f73bc0cea5c311f6a516e9010061998ad

WORKDIR /opt/egov

# Copy artifact from the build stage
COPY --from=build /app/target/*.jar ./application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]
