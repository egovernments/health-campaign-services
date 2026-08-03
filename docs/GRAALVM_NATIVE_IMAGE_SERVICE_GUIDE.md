# GraalVM Native Image Service Guide

## Purpose

This guide documents the repeatable changes needed to enable a Spring Boot service for GraalVM native-image builds and deployment through GitHub Actions and ArgoCD.

## Scope

Use this guide when enabling GraalVM native image builds for a service under:
- `health-services/<service-name>`

Examples:
- `health-services/household`
- `health-services/referralmanagement`
- `health-services/individual`

## High-Level Pattern

For each service, the required work usually falls into 4 buckets:

1. App repo build changes
2. Native runtime hinting
3. Service code cleanup for native runtime
4. Deployment repo overrides

Do not start by adding large reflection JSON files or broad class-initialization flags. The working pattern here was to keep the setup small and fix concrete failures one at a time.

## 1. Application Build Changes

### 1.1 Add GraalVM native Maven plugin

In `health-services/<service>/pom.xml`:

Add property:

```xml
<native.maven.plugin.version>0.10.4</native.maven.plugin.version>
```

Add plugin:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>${native.maven.plugin.version}</version>
    <configuration>
        <buildArgs>
            <buildArg>--no-fallback</buildArg>
            <buildArg>--enable-url-protocols=http,https</buildArg>
            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
            <buildArg>-H:+AddAllCharsets</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

Notes:
- Do not add `process-aot` manually for normal builds.
- Let the native build path drive AOT.

### 1.2 Use a minimal native-image.properties

Create:

- `health-services/<service>/src/main/resources/META-INF/native-image/native-image.properties`

Use this pattern:

```properties
Args = --no-fallback \
       --enable-url-protocols=http,https \
       --enable-all-security-services \
       -H:+ReportExceptionStackTraces \
       -H:+AddAllCharsets \
       -Djava.awt.headless=true \
       --initialize-at-run-time=org.apache.kafka \
       --initialize-at-run-time=org.postgresql.Driver \
       --initialize-at-run-time=org.postgresql.util.SharedTimer \
       --initialize-at-run-time=org.postgresql.PGProperty \
       --initialize-at-run-time=org.flywaydb.core \
       --initialize-at-run-time=org.egov.tracer.kafka
```

Notes:
- Do not start with `JavaArgs = -Xmx6g` unless native compilation is actually running out of memory.
- Do not start with `ReflectionConfigurationFiles`, `ResourceConfigurationFiles`, or `SerializationConfigurationFiles`.
- Do not add broad `--initialize-at-build-time` rules unless a real error requires them.

### 1.3 Upgrade tracer and remove OTel baggage

In `pom.xml`, move `tracer` to:

```xml
<dependency>
    <groupId>org.egov.services</groupId>
    <artifactId>tracer</artifactId>
    <version>2.9.2-SNAPSHOT</version>
    <exclusions>
        <exclusion>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-spring-boot-starter</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-spring-kafka-2.7</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-jdbc</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-logging</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-extension-autoconfigure-spi</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-otel</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Why:
- The OTel stack caused native-image analysis/runtime issues and was not needed to get the services working.

### 1.4 Align Flyway if needed

If the service still uses older Flyway, align it to:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>10.21.0</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <version>10.21.0</version>
</dependency>
```

Why:
- older Flyway versions can fail against Spring Boot 3.4.x native startup with binary incompatibilities.

## 2. Native Runtime Hints

### 2.1 Add a service-local GraalVM configuration class

Create:

- `health-services/<service>/src/main/java/org/egov/<service>/config/GraalVMConfiguration.java`

Pattern:

```java
package org.egov.<service>.config;

import org.egov.tracer.kafka.deserializer.HashMapDeserializer;
import org.egov.tracer.kafka.deserializer.ISTTimeZoneHashMapDeserializer;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GraalVMConfiguration.ServiceRuntimeHints.class)
public class GraalVMConfiguration {

    static class ServiceRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection()
                    .registerType(TypeReference.of(HashMapDeserializer.class), MemberCategory.values())
                    .registerType(TypeReference.of(ISTTimeZoneHashMapDeserializer.class), MemberCategory.values())
                    .registerType(TypeReference.of("org.egov.tracer.kafka.KafkaTemplateLoggingInterceptors"),
                            MemberCategory.values());
        }
    }
}
```

### 2.2 Add Flyway hint only if required

If native startup fails with a reflection error like:

```text
MissingReflectionRegistrationError:
org.flywaydb.core.internal.publishing.PublishingConfigurationExtension
```

Add:

```java
.registerType(
    TypeReference.of("org.flywaydb.core.internal.publishing.PublishingConfigurationExtension"),
    MemberCategory.INVOKE_PUBLIC_METHODS
)
```

Do not add this proactively to every service unless the service actually hits that Flyway path.

## 3. Service Code Cleanup for Native Runtime

### 3.1 Remove constructor injection of HttpServletRequest

This was a repeated native runtime issue.

Bad pattern:

```java
private final HttpServletRequest request;

public SomeController(HttpServletRequest request, ...) {
    this.request = request;
}
```

Why it fails:
- Spring creates a scoped proxy for `HttpServletRequest`
- GraalVM then fails on reflective proxy access

Good pattern:

```java
public ResponseEntity<?> someEndpoint(..., HttpServletRequest request) {
    request.getRequestURI();
}
```

Apply this to every controller constructor that injects `HttpServletRequest`.

## 4. Shared GraalVM Dockerfile

Files:
- `build/graalvm/maven/Dockerfile`
- `build/graalvm/maven/Dockerfile.community`

Working build command:

```sh
mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile
```

Important:
- `package` alone is not enough in this repo
- the Docker build must explicitly invoke `native:compile`

### Runtime binary copy

The Dockerfile must not assume a hardcoded output name like `household`.

Use:

```sh
APP_NAME=$(basename "${WORK_DIR}")
cp "/app/target/${APP_NAME}" /app/target/application
```

Then use:
- `/opt/egov/application` for `COPY`
- `/opt/egov/application` for `ENTRYPOINT`
- `/opt/egov/application --health` for `HEALTHCHECK`

## 5. GitHub Actions Wiring

### 5.1 Add workflow input

In:
- `.github/workflows/build.yaml`

Add a pipeline option like:

```yaml
- individual-graalvm-native
```

### 5.2 Add build-config mapping

In:
- `build/build-config.yml`

Add:

```yaml
- name: "builds/health-campaign-services/health-services/<service>-graalvm-native"
  build:
    - work-dir: "health-services/<service>"
      image-name: "<service>"
      dockerfile: "build/graalvm/maven/Dockerfile"
    - work-dir: "health-services/<service>/src/main/resources/db"
      image-name: "<service>-db"
```

## 6. Deployment Configuration Changes

### 6.1 Understand the deployment layering

ArgoCD app set:
- `config-as-code/helm/charts/argo-cd/kebbi/kebbi-app-set-health.yaml`

This combines:
- service chart:
  `config-as-code/helm/charts/health-services/<service>`
- shared env file:
  `config-as-code/environments/kebbi-central-uat.yaml`

### 6.2 Common chart behavior

The common chart already injects:
- `SPRING_DATASOURCE_URL`
- `SPRING_FLYWAY_ENABLED=false`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

from:
- `config-as-code/helm/charts/common/values.yaml`

But it also injects:
- `FLYWAY_URL`

instead of:
- `SPRING_FLYWAY_URL`

Do not change the common chart blindly if other legacy services may still depend on `FLYWAY_URL`.

### 6.3 Safer deployment fix for a single service

If one service still tries `spring.flyway.url=jdbc:postgresql://localhost:5432/...` at runtime, add a service-specific override in:

- `config-as-code/helm/charts/health-services/<service>/values.yaml`

Pattern:

```yaml
- name: SPRING_FLYWAY_URL
  valueFrom:
    configMapKeyRef:
      name: egov-config
      key: db-url
```

Do not add `SPRING_FLYWAY_ENABLED` if the common chart already defines it, otherwise ArgoCD will reject the Deployment with duplicate env keys.

## 7. How to Triage Failures

Do not add random hints first. Use the first real failure.

### Build-time native-image failure

Look for:
- the first actual GraalVM exception
- not just Maven's final `non-zero result`

### Runtime startup failure

Typical cases seen:

1. `HttpServletRequest` proxy failure
- fix controller injection style

2. Kafka deserializer class not found
- add tracer deserializer hints

3. Kafka interceptor class not found
- add `KafkaTemplateLoggingInterceptors` hint

4. Flyway `NoSuchMethodError`
- upgrade Flyway version

5. Flyway `MissingReflectionRegistrationError`
- add targeted Flyway method hint

6. App tries connecting to `localhost` in cluster
- check deployment env overrides, not app defaults

## 8. Recommended Workflow For Next Service

For the next service:

1. Copy the minimal native setup from a working service.
2. Add GitHub Actions/build-config mapping.
3. Remove `HttpServletRequest` constructor injection if present.
4. Add tracer runtime hints.
5. Exclude the OTel stack from `tracer`.
6. Run the native pipeline.
7. Fix only the first concrete failure.

Do not start with:
- huge reflection JSON files
- broad build-time initialization rules
- common-chart changes affecting all services

## 9. Files Commonly Touched

Application repo:
- `health-services/<service>/pom.xml`
- `health-services/<service>/src/main/resources/META-INF/native-image/native-image.properties`
- `health-services/<service>/src/main/java/.../config/GraalVMConfiguration.java`
- `health-services/<service>/src/main/java/.../web/controllers/*.java`
- `.github/workflows/build.yaml`
- `build/build-config.yml`
- `build/graalvm/maven/Dockerfile`
- `build/graalvm/maven/Dockerfile.community`

Deployment repo:
- `config-as-code/helm/charts/health-services/<service>/values.yaml`

## 10. Final Advice

The stable pattern here is:
- keep the native config small
- fix real failures one at a time
- prefer code/deployment cleanup over large reflection metadata dumps

If a service has similar Spring Boot, Kafka, tracer, and Flyway patterns, the same approach should work with only small service-specific adjustments.
