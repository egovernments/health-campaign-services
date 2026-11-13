# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-17@sha256:4015718012bbf1113ec6cfae2b950be328d90265ceb60f92b26c3ea7c4d14ee8 AS build
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
