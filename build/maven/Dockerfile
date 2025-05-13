# ─── BUILD STAGE ────────────────────────────────────────────────────────────────
FROM maven:3.9.6-amazoncorretto-17 AS build
ARG WORK_DIR
WORKDIR /app

# Copy project files
COPY ${WORK_DIR}/pom.xml       ./pom.xml
COPY build/maven/start.sh      ./start.sh
COPY ${WORK_DIR}/src           ./src

# Ensure start.sh is Unix-style and executable (in case of CRLFs in your repo)
RUN apt-get update && \
    apt-get install -y dos2unix && \
    dos2unix start.sh && \
    chmod +x start.sh && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Tell Maven/`javac` to export the JDK compiler package to unnamed modules
ENV MAVEN_OPTS="--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED"

# Build (skip tests to speed up CI; remove -DskipTests if you need them)
RUN mvn -B -f pom.xml clean package -DskipTests

# ─── RUNTIME STAGE ──────────────────────────────────────────────────────────────
FROM amazoncorretto:17-alpine AS runtime

WORKDIR /opt/egov

# Install dos2unix so we can fix line endings on the start script
RUN apk add --no-cache dos2unix

# Copy the jar and the startup script
COPY --from=build /app/target/*.jar     ./app.jar
COPY --from=build /app/start.sh         ./start.sh

# Fix line endings & permissions
RUN dos2unix start.sh && \
    chmod +x start.sh

# (Optional) verify the container architecture at runtime
RUN uname -m

# Start your app
CMD ["./start.sh"]
