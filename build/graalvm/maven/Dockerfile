# syntax=docker/dockerfile:1
# Multi-stage build for GraalVM native image with optimized caching
FROM container-registry.oracle.com/graalvm/native-image:25-ol9 AS build
ARG WORK_DIR
WORKDIR /app

# Install Maven
RUN microdnf install -y maven && \
    microdnf clean all

# Copy only POM first to cache dependencies separately
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY build/maven/start.sh ./start.sh

# Download dependencies - this layer will be cached unless pom.xml changes
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline -Pnative || true

# Copy source code after dependencies are cached
COPY ${WORK_DIR}/src ./src

# Build the native image with BuildKit cache mount for Maven repository
# This ensures Maven dependencies are cached across builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile

# Resolve the native executable name from the module directory.
RUN APP_NAME=$(basename "${WORK_DIR}") && \
    cp "/app/target/${APP_NAME}" /app/target/application

# Lightweight runtime image
FROM oraclelinux:9-slim

WORKDIR /opt/egov

# Install required runtime dependencies
RUN microdnf install -y glibc zlib && \
    microdnf clean all

# Copy the native executable
COPY --from=build /app/target/application /opt/egov/application

# Create non-root user
RUN useradd -r -s /bin/false egov && \
    chown -R egov:egov /opt/egov

USER egov

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD ["/opt/egov/application", "--health"] || exit 1

# Expose application port
EXPOSE 8080

# Run the native image
ENTRYPOINT ["/opt/egov/application"]
