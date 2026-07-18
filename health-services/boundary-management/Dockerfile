# Use Node.js base image with version 20
FROM node:20 AS build

# Set working directory
WORKDIR /app

# Set build arguments
ARG BRANCH_NAME
ARG ACTION_NUMBER
ARG COMMIT_ID
ARG WORK_DIR

# Set environment variables based on build arguments
ENV BRANCH_NAME=$BRANCH_NAME
ENV ACTION_NUMBER=$ACTION_NUMBER
ENV COMMIT_ID=$COMMIT_ID

# JS old-space ceiling sized to 75% of the 2Gi pod memory_limits (DIGIT-DevOps chart) so RSS
# (heap + V8 external/Buffers + native + baseline) stays under the container limit — a ceiling
# equal to the limit gets kernel-OOMKilled (exit 137) before V8's own GC pressure kicks in.
# Keep in sync with package.json "serve" (which re-sets NODE_OPTIONS inline and wins at runtime).
ENV NODE_OPTIONS="--max-old-space-size=1536"

# Copy package.json and yarn.lock (if exists). Build context is the repo root and the
# service dir is passed via WORK_DIR (matches build/maven/Dockerfile + the generic build.yaml).
COPY ${WORK_DIR}/package.json ./

# Install dependencies
RUN yarn install

# Optionally, you can add a label with the commit ID
LABEL commit_id=$COMMIT_ID

# Copy the rest of the application code
COPY ${WORK_DIR}/ ./

# Expose the port your app runs on
EXPOSE 3000

# Start the app
CMD ["yarn", "prod"]
