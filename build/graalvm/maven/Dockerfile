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

# Build the native image using Spring Boot's inherited native profile.
# That profile wires AOT processing, reachability metadata, and compile-no-fork
# for the native-maven-plugin in a single Maven invocation.
RUN mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package

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
