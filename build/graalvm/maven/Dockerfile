# Multi-stage build for GraalVM native image
FROM container-registry.oracle.com/graalvm/native-image:25-ol9 AS build
ARG WORK_DIR
WORKDIR /app

# Install Maven
RUN microdnf install -y maven && \
    microdnf clean all

# Copy project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src

# Build the native image. The native profile enables Spring AOT, but this
# project still needs the native-image goal invoked explicitly to emit the
# executable under /app/target/household.
RUN mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile

# Resolve the module artifactId so the runtime stage can copy the right binary.
RUN ARTIFACT_ID=$(sed -n 's:.*<artifactId>\\(.*\\)</artifactId>.*:\\1:p' /app/pom.xml | head -n 1) && \
    cp "/app/target/${ARTIFACT_ID}" /app/target/application

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
