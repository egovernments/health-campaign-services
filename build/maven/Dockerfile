FROM --platform=$BUILDPLATFORM maven:3.9.6-amazoncorretto-8-debian AS build
ARG WORK_DIR
ARG DEPS_CACHE_BUST=""

WORKDIR /app

COPY build/maven/settings.xml /root/.m2/settings.xml

COPY ${WORK_DIR}/pom.xml ./pom.xml
RUN if [ -n "$DEPS_CACHE_BUST" ]; then \
        mvn -B dependency:resolve dependency:resolve-plugins -U; \
    else \
        mvn -B dependency:resolve dependency:resolve-plugins -nsu; \
    fi

COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src
RUN mvn -B package -DskipTests -nsu

FROM eclipse-temurin:8-jre AS runtime

WORKDIR /opt/egov

COPY --from=build /app/target/*.jar ./application.jar
COPY --from=build /app/start.sh ./start.sh

RUN chmod +x ./start.sh

ENTRYPOINT ["sh", "./start.sh"]
