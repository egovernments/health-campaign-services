# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-8 AS build
ARG WORK_DIR
WORKDIR /app

COPY build/maven/settings.xml ./settings.xml
COPY ${WORK_DIR}/pom.xml ./pom.xml

RUN mvn -B -s settings.xml dependency:go-offline

COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src

# ARG declared late: earlier placement injects it into the env of the go-offline RUN and busts the dependency layer cache whenever the value changes
ARG SKIP_TESTS=false
RUN mvn -B -s settings.xml package -DskipTests=${SKIP_TESTS}

# ─────────────── RUNTIME STAGE ───────────────
FROM openjdk:8-jdk-alpine AS runtime
WORKDIR /opt/egov

COPY --from=build /app/target/*.jar ./application.jar
COPY --from=build /app/start.sh ./start.sh

RUN chmod +x ./start.sh

ENTRYPOINT ["sh", "./start.sh"]
