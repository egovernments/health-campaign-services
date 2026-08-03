# GraalVM Native Build Optimization Guide

## Current Build Time: ~20 minutes
## Target Build Time: <10 minutes

## Quick Wins (Implement First)

### 1. Enable Maven Dependency Caching
Add Maven cache to the GitHub Actions workflow to avoid re-downloading dependencies:

```yaml
- name: Cache Maven Dependencies
  uses: actions/cache@v3
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-
```

### 2. Optimize Docker Layer Caching
Modify the Dockerfile to better leverage layer caching:

```dockerfile
# Copy only POM first for dependency resolution
COPY ${WORK_DIR}/pom.xml ./pom.xml
RUN mvn dependency:go-offline -B -ntp

# Then copy source (changes more frequently)
COPY ${WORK_DIR}/src ./src
RUN mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile
```

### 3. Use BuildKit Cache Mounts
Enable BuildKit cache mounts for Maven repository:

```dockerfile
# Enable BuildKit
# syntax=docker/dockerfile:1
FROM container-registry.oracle.com/graalvm/native-image:25-ol9 AS build

# Use cache mount for Maven
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f /app/pom.xml -Pnative -DskipTests package native:compile
```

## Advanced Optimizations

### 4. Parallel Build Jobs
Instead of building both architectures sequentially, optimize the matrix strategy:

```yaml
build-matrix:
  strategy:
    matrix:
      arch: [amd64]  # Focus on primary architecture first
    max-parallel: 2  # Limit parallel jobs to avoid resource contention
```

### 5. GraalVM Build Optimizations
Add these build arguments to speed up native compilation:

```xml
<plugin>
  <groupId>org.graalvm.buildtools</groupId>
  <artifactId>native-maven-plugin</artifactId>
  <configuration>
    <buildArgs>
      <buildArg>--no-fallback</buildArg>
      <buildArg>--enable-url-protocols=http,https</buildArg>
      <buildArg>-H:+ReportExceptionStackTraces</buildArg>
      <buildArg>-H:+AddAllCharsets</buildArg>
      <!-- Optimization flags -->
      <buildArg>-march=native</buildArg>
      <buildArg>-O2</buildArg>  <!-- Balanced optimization -->
      <buildArg>--parallelism=4</buildArg>  <!-- Use 4 threads -->
      <buildArg>--gc=serial</buildArg>  <!-- Faster build with serial GC -->
    </buildArgs>
  </configuration>
</plugin>
```

### 6. Use Remote Docker Cache
Implement remote cache with GitHub Container Registry:

```yaml
- name: Build and Push Application Image
  run: |
    docker buildx build \
      --platform ${{ matrix.platform }} \
      --cache-from=type=registry,ref=ghcr.io/${{ github.repository }}-cache:latest \
      --cache-to=type=registry,ref=ghcr.io/${{ github.repository }}-cache:latest,mode=max \
      --tag egovio/${{ needs.resolve-config.outputs.service_image_name }}:${{ needs.resolve-config.outputs.tag }} \
      --push \
      .
```

### 7. Pre-built Base Image
Create a custom base image with Maven and common dependencies pre-installed:

```dockerfile
# Create base image (build once, reuse many times)
FROM container-registry.oracle.com/graalvm/native-image:25-ol9 AS base
RUN microdnf install -y maven && microdnf clean all
# Pre-download common dependencies
COPY pom-base.xml /tmp/pom.xml
RUN mvn -f /tmp/pom.xml dependency:go-offline
```

### 8. Conditional Builds
Skip ARM64 builds for non-production branches:

```yaml
matrix:
  include:
    - arch: amd64
      platform: linux/amd64
      runner: ubuntu-latest
    - arch: arm64
      platform: linux/arm64
      runner: ubuntu-24.04-arm
      if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/master'
```

## Implementation Priority

1. **Immediate (5-min reduction):**
   - Maven dependency caching
   - Docker layer optimization

2. **Short-term (3-5 min reduction):**
   - BuildKit cache mounts
   - GraalVM build flags optimization

3. **Medium-term (2-3 min reduction):**
   - Remote Docker cache
   - Pre-built base images

4. **Long-term considerations:**
   - Consider using GitHub's larger runners for GraalVM builds
   - Investigate using Buildpacks for automated optimization
   - Consider build farms for native compilation

## Monitoring Build Performance

Track these metrics:
- Maven dependency download time
- Native compilation time
- Docker layer push/pull time
- Total workflow duration

## Alternative: JVM Build for Development

For development branches, consider using regular JVM builds instead of native:

```yaml
- name: Build JVM Image (Dev)
  if: github.ref != 'refs/heads/main'
  run: |
    mvn -B -ntp package -DskipTests
    # Regular Docker build without native compilation
```

This can reduce build time from 20 minutes to ~5 minutes for development iterations.