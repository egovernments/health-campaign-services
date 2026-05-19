FROM maven:3.9.6-amazoncorretto-17 AS build
ARG WORK_DIR
# Pass any non-empty value to force re-download all deps: --build-arg DEPS_CACHE_BUST=1
ARG DEPS_CACHE_BUST=""

WORKDIR /app

# Bake mirror routing: standard artifacts go to Central, egov artifacts go to Nexus
COPY build/maven-java17/settings.xml /root/.m2/settings.xml

# ── PHASE 1: Central (non-egov) dependencies ─────────────────────────────────
# This Docker layer is invalidated only when pom.xml changes or DEPS_CACHE_BUST is set.
# The BuildKit cache mount persists .m2/repository across all builds on this host.
# -Dexcludes: skips egov JARs (org.egov, org.egov.services, org.egov.common)
# -nsu: overrides updatePolicy=always — no SNAPSHOT re-checks (normal mode)
# -U:   force-checks all remotes for updates (only when DEPS_CACHE_BUST is set)
COPY ${WORK_DIR}/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2/repository,id=maven-repo \
    if [ -n "$DEPS_CACHE_BUST" ]; then \
        mvn -B dependency:resolve \
            -Dexcludes="org.egov:*,org.egov.services:*,org.egov.common:*" \
            -U -q; \
    else \
        mvn -B dependency:resolve \
            -Dexcludes="org.egov:*,org.egov.services:*,org.egov.common:*" \
            -nsu -q; \
    fi

# ── PHASE 2: egov (Nexus) dependencies ───────────────────────────────────────
# Separate layer so bumping an egov dep version only re-runs this phase.
# Central deps from Phase 1 are already in the BuildKit cache — skipped instantly.
RUN --mount=type=cache,target=/root/.m2/repository,id=maven-repo \
    if [ -n "$DEPS_CACHE_BUST" ]; then \
        mvn -B dependency:go-offline -U -q; \
    else \
        mvn -B dependency:go-offline -nsu -q; \
    fi

# ── PHASE 3: Compile (fully offline) ─────────────────────────────────────────
# -o: offline mode — zero network calls during compile. All deps must be in cache.
# This layer is invalidated only when source files change.
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src
RUN --mount=type=cache,target=/root/.m2/repository,id=maven-repo \
    mvn -B package -DskipTests -o

# ── Runtime image ─────────────────────────────────────────────────────────────
FROM amazoncorretto:17-alpine

WORKDIR /opt/egov

COPY --from=build /app/target/*.jar /app/start.sh /opt/egov/

RUN dos2unix /opt/egov/start.sh && chmod +x /opt/egov/start.sh

RUN uname -m

CMD ["/opt/egov/start.sh"]
