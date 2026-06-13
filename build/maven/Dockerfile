
FROM --platform=linux/amd64 egovio/alpine-maven-builder-jdk-8:1-master-NA-6036091e AS build
ARG WORK_DIR
WORKDIR /app

# copy the project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY build/maven/start.sh ./start.sh

# not useful for stateless builds
# RUN mvn -B dependency:go-offline

COPY ${WORK_DIR}/src ./src
RUN mvn -B -f /app/pom.xml package


# Create runtime image — amazoncorretto:8-alpine ships native manifests for
# both linux/amd64 and linux/arm64, unlike the egovio wrapper image.
FROM amazoncorretto:8-alpine


WORKDIR /opt/egov

COPY --from=build /app/target/*.jar /app/start.sh /opt/egov/

RUN chmod +x /opt/egov/start.sh

CMD ["/opt/egov/start.sh"]
