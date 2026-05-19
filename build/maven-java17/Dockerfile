FROM --platform=$BUILDPLATFORM maven:3.9.6-amazoncorretto-17 AS build
ARG WORK_DIR
ARG DEPS_CACHE_BUST=""

WORKDIR /app

COPY build/maven-java17/settings.xml /root/.m2/settings.xml

COPY ${WORK_DIR}/pom.xml ./pom.xml
RUN if [ -n "$DEPS_CACHE_BUST" ]; then \
        mvn -B dependency:resolve dependency:resolve-plugins -U; \
    else \
        mvn -B dependency:resolve dependency:resolve-plugins -nsu; \
    fi

COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src
RUN mvn -B package -DskipTests -nsu

FROM amazoncorretto:17-alpine

WORKDIR /opt/egov

COPY --from=build /app/target/*.jar /app/start.sh /opt/egov/

RUN dos2unix /opt/egov/start.sh && chmod +x /opt/egov/start.sh

CMD ["/opt/egov/start.sh"]
