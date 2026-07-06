# Multi-stage build for GraalVM native image
FROM ghcr.io/graalvm/graalvm-ce:ol9-java21-22.3.3 AS build
ARG WORK_DIR
WORKDIR /app

# Install Maven and native-image
RUN microdnf install -y maven && \
    gu install native-image

# Copy project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src

# Build the native image
RUN mvn -Pnative clean native:compile -DskipTests

# Lightweight runtime image
FROM oraclelinux:9-slim

WORKDIR /opt/egov

# Install required runtime dependencies
RUN microdnf install -y glibc zlib && \
    microdnf clean all

# Copy the native executable
COPY --from=build /app/target/household /opt/egov/household

# Create non-root user
RUN useradd -r -s /bin/false egov && \
    chown -R egov:egov /opt/egov

USER egov

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD ["/opt/egov/household", "--health"] || exit 1

# Expose application port
EXPOSE 8080

# Run the native image
ENTRYPOINT ["/opt/egov/household"]