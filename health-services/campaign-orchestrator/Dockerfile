FROM openjdk:25-jdk-slim

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

WORKDIR /app

COPY target/campaign-orchestrator-*.jar campaign-orchestrator.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Virtual threads require no extra JVM flags in Java 21+
ENV JAVA_OPTS="-Xms512m -Xmx2g"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar campaign-orchestrator.jar"]
