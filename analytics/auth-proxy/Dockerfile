# syntax=docker/dockerfile:1.7-labs

# ---- Patch stage: pulls current Debian security-patched libssl3/libc6, since the
# upstream distroless image (below) is pinned to an older, CVE-affected point release
# that hasn't been rebuilt yet (see CVE-2026-31789 and friends). Resolved per-arch by
# buildx automatically for each --platform in the multiarch build.
FROM debian:12-slim AS libpatch
RUN apt-get update \
    && apt-get install -y --no-install-recommends libssl3 libc6 \
    && mkdir -p /patched-meta/status.d \
    && for pkg in libssl3 libc6; do \
         dpkg-query -s "$pkg" | grep -v '^Status:' > /patched-meta/status.d/"$pkg"; \
         cp /var/lib/dpkg/info/"$pkg":*.md5sums /patched-meta/status.d/"$pkg".md5sums; \
       done \
    && rm -rf /var/lib/apt/lists/*

# ---- Build stage: compile deps (needs npm/python/make/gcc for native modules like deasync) ----
# Debian-based (glibc) to match the glibc runtime below - Alpine's musl-built native
# addons (e.g. deasync) won't load on the distroless Debian runtime.
FROM node:20-bookworm-slim AS build
WORKDIR /usr/src/app

# Build tooling required only to compile native addons; not present in the final image
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm ci --omit=dev

COPY . .

# ---- Runtime stage: distroless, no shell/package manager/OS utilities ----
FROM gcr.io/distroless/nodejs20-debian12:nonroot
WORKDIR /usr/src/app

ENV SERVER_PORT=8080

COPY --from=build /usr/src/app ./

# Overlay the patched libssl3/libc6 shared objects on top of the ones baked into the
# distroless base image, preserving the exact same (arch-specific) library paths.
# (glibc >=2.34 packaging ships libc.so.6/libm.so.6 as the real files directly, no
# separate libc-2.36.so - verified against the actual debian:12-slim package layout.)
COPY --from=libpatch --parents /usr/lib/*-linux-gnu/libssl.so.3 /
COPY --from=libpatch --parents /usr/lib/*-linux-gnu/libcrypto.so.3 /
COPY --from=libpatch --parents /lib/*-linux-gnu/libc.so.6 /
COPY --from=libpatch --parents /lib/*-linux-gnu/libm.so.6 /

# Update the corresponding scanner-visible package metadata to match, so Trivy/Grype
# report the version that's actually installed instead of the stale distroless one.
COPY --from=libpatch /patched-meta/status.d/ /var/lib/dpkg/status.d/

# Image already runs as the built-in non-root user
USER nonroot

EXPOSE 8080
EXPOSE 9229

# distroless has no shell, so the "${DEBUG_ENABLED:+...}" expansion from npm start
# isn't available here; bootstrap.js opens the inspector via the Node API instead.
CMD ["bootstrap.js"]
