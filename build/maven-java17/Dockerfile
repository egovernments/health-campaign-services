# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
ARG WORK_DIR
WORKDIR /app

COPY build/maven/settings.xml ./settings.xml
COPY ${WORK_DIR}/pom.xml ./pom.xml

RUN mvn -B -s settings.xml dependency:go-offline

COPY ${WORK_DIR}/src ./src

# ARG declared late: earlier placement injects it into the env of the go-offline RUN and busts the dependency layer cache whenever the value changes
ARG SKIP_TESTS=false
RUN mvn -B -s settings.xml package -DskipTests=${SKIP_TESTS}

# ─────────────── RUNTIME STAGE ───────────────
FROM gcr.io/distroless/java17-debian12:nonroot

WORKDIR /opt/egov

# Copy artifact from the build stage
COPY --from=build /app/target/*.jar ./application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]