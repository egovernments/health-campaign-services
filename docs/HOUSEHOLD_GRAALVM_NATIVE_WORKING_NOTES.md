# Household GraalVM Native Image Working Notes

## Status

`household` now builds as a GraalVM native image and starts successfully on the `household-graalvm-native` branch.

Branch tip:
- `5eac3ae5f1` - Avoid servlet request proxy in household controller

Related commits in the working sequence:
- `aaaf79efdc` - Exclude OpenTelemetry stack from household tracer
- `a166342b04` - Invoke native-image compile in GraalVM Docker build
- `a9d1999de5` - Fix household GraalVM native build path

## What Changed

### 1. GraalVM Docker build path

Files:
- `build/graalvm/maven/Dockerfile`
- `build/graalvm/maven/Dockerfile.community`

Working build command:

```sh
mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile
```

Why:
- `-Pnative package` alone ran Spring AOT and packaged the jar, but did not emit `/app/target/household`
- the runtime image stage expected the native executable, so `native:compile` had to be invoked explicitly

### 2. Household native-image properties simplified

File:
- `health-services/household/src/main/resources/META-INF/native-image/native-image.properties`

What changed:
- removed broad build-time initialization rules
- removed file-path based `ReflectionConfigurationFiles`, `ResourceConfigurationFiles`, and `SerializationConfigurationFiles`
- kept a smaller runtime-init list for Kafka, PostgreSQL, Flyway, and tracer Kafka classes
- set `JavaArgs = -Xmx6g`

Why:
- the earlier configuration mixed Spring AOT hints, manual JSON config, and aggressive native-image overrides
- that made failures harder to diagnose and increased the chance of analysis-time conflicts

### 3. Spring Boot AOT flow cleaned up

File:
- `health-services/household/pom.xml`

What changed:
- removed explicit `spring-boot:process-aot` execution from the normal plugin configuration

Why:
- AOT should not run on every normal build path
- the native build now drives AOT through the native pipeline instead

### 4. OpenTelemetry stack excluded from `household`

File:
- `health-services/household/pom.xml`

What changed:
- added exclusions on `org.egov.services:tracer` for:
  - `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`
  - `io.opentelemetry.instrumentation:opentelemetry-spring-kafka-2.7`
  - `io.opentelemetry.instrumentation:opentelemetry-jdbc`
  - `io.opentelemetry:opentelemetry-exporter-logging`
  - `io.opentelemetry:opentelemetry-exporter-otlp`
  - `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure`
  - `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi`
  - `io.micrometer:micrometer-registry-prometheus`
  - `io.micrometer:micrometer-tracing-bridge-otel`

Why:
- the native-image classpath showed a large OTel alpha stack being pulled transitively from `tracer`
- previous failures were already centered around OpenTelemetry initialization and compatibility
- excluding that stack reduced the native surface area without changing household business logic

After exclusions, the remaining metrics-related dependencies were reduced to the basic Spring/Micrometer pieces coming from the web and actuator stack.

### 5. Removed servlet request scoped proxy from controller construction

File:
- `health-services/household/src/main/java/org/egov/household/web/controllers/HouseholdApiController.java`

What changed:
- removed constructor injection of `HttpServletRequest`
- added `HttpServletRequest` as a method parameter only on endpoints that use `getRequestURI()`

Why:
- the native image started, but failed at runtime with:

```text
MissingReflectionRegistrationError: Cannot reflectively access the proxy class inheriting ['jakarta.servlet.http.HttpServletRequest']
```

- constructor-injecting `HttpServletRequest` into a singleton controller forced Spring to create a scoped proxy
- GraalVM rejected that proxy
- moving the request object to handler method parameters removed the proxy requirement entirely

## Working Trigger Path

GitHub Actions workflow:
- `.github/workflows/build.yaml`

Workflow input:
- `pipeline_name=household-graalvm-native`

Build config mapping:
- `build/build-config.yml`

Resolved Dockerfile:
- `build/graalvm/maven/Dockerfile`

## Key Errors Encountered

### 1. Native executable missing from Docker build

Error:

```text
COPY --from=build /app/target/household /opt/egov/household
"/app/target/household": not found
```

Cause:
- Maven produced only the jar

Fix:
- invoke `native:compile` explicitly

### 2. Native-image wrapper failure with huge classpath

Observed signal:
- OpenTelemetry starter, exporters, Kafka instrumentation, JDBC instrumentation, and micrometer OTel bridge were all on the native-image classpath

Cause:
- transitive dependencies from `org.egov.services:tracer`

Fix:
- exclude the OTel and tracing stack from `household`

### 3. Runtime proxy failure after native image started

Error:

```text
MissingReflectionRegistrationError: Cannot reflectively access the proxy class inheriting ['jakarta.servlet.http.HttpServletRequest']
```

Cause:
- singleton controller constructor depended on `HttpServletRequest`

Fix:
- request object moved to handler method parameters

## Current Practical Guidance

- Use this `household` setup as the baseline for the next service, not the older over-configured native-image attempt
- Prefer removing proxy-heavy or instrumentation-heavy runtime features before adding more reflection metadata
- When the native image fails, capture the first real GraalVM error block or first runtime exception, not just Maven's final `non-zero result`

## Files Changed In This Working Pass

- `build/graalvm/maven/Dockerfile`
- `build/graalvm/maven/Dockerfile.community`
- `health-services/household/pom.xml`
- `health-services/household/src/main/resources/META-INF/native-image/native-image.properties`
- `health-services/household/src/main/java/org/egov/household/web/controllers/HouseholdApiController.java`
