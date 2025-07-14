FROM maven:3.9.6-amazoncorretto-17 AS build
ARG WORK_DIR
WORKDIR /app

# Copy only pom.xml and resolve dependencies first
COPY ${WORK_DIR}/pom.xml ./pom.xml

# ✅ Cache Maven dependencies by resolving them up front
RUN --mount=type=cache,target=/root/.m2 mvn -B -f ./pom.xml dependency:go-offline

# Now copy full source and build
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src

# ✅ Perform the actual build (will use cached dependencies)
RUN --mount=type=cache,target=/root/.m2 mvn -B -f ./pom.xml package

# Runtime image – using a multi-arch base image
FROM amazoncorretto:17-alpine

WORKDIR /opt/egov

# Copy artifacts from the build stage
COPY --from=build /app/target/*.jar /app/start.sh /opt/egov/

# Ensure the start script has correct line endings and is executable
RUN dos2unix /opt/egov/start.sh && chmod +x /opt/egov/start.sh

# Verify architecture inside the container
RUN uname -m

CMD ["/opt/egov/start.sh"]
