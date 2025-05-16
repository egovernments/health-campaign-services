# ─── BUILD STAGE ────────────────────────────────────────────────────────────────
FROM maven:3.9.6-amazoncorretto-17 AS build
ARG WORK_DIR
WORKDIR /app

# Copy project files
COPY ${WORK_DIR}/pom.xml    ./pom.xml
COPY build/maven/start.sh   ./start.sh
COPY ${WORK_DIR}/src        ./src

# Make your script executable (line endings fixed later)
RUN chmod +x start.sh

# Build the fat JAR
RUN mvn -B -f pom.xml clean package -DskipTests

# ─── RUNTIME STAGE ──────────────────────────────────────────────────────────────
FROM amazoncorretto:17-alpine AS runtime

WORKDIR /opt/egov

# Install dos2unix on Alpine
RUN apk add --no-cache dos2unix

# Copy over the JAR and start script
COPY --from=build /app/target/*.jar   ./app.jar
COPY --from=build /app/start.sh       ./start.sh

# Normalize line endings & ensure executable bit
RUN dos2unix start.sh && chmod +x start.sh

# (Optional) check arch at runtime
RUN uname -m

CMD ["./start.sh"]