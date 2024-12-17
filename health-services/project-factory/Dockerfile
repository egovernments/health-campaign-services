# Use Node.js base image with version 20
FROM node:20 AS build

# Set working directory
WORKDIR /app

# Set build arguments
ARG BRANCH_NAME
ARG ACTION_NUMBER
ARG COMMIT_ID

# Set environment variables based on build arguments
ENV BRANCH_NAME=$BRANCH_NAME
ENV ACTION_NUMBER=$ACTION_NUMBER
ENV COMMIT_ID=$COMMIT_ID
ENV NODE_OPTIONS="--max-old-space-size=2048"
ENV YARN_CACHE_FOLDER=/tmp/.yarn-cache  # Disable cache by setting custom folder

# Set proxy settings if Jenkins or environment is behind a proxy (uncomment if needed)
# ENV HTTP_PROXY=http://your-proxy.com:8080
# ENV HTTPS_PROXY=http://your-proxy.com:8080

# Copy package.json and yarn.lock (if exists)
COPY package.json ./

# Install dependencies with various yarn config options
RUN yarn config set network-timeout 600000 && \
    yarn config set fetch-retries 5 && \
    yarn config set fetch-retry-factor 10 && \
    yarn config set fetch-retry-mintime 1000 && \
    yarn cache clean --force && \
    yarn install --force --no-cache --prefer-offline --no-lockfile  # Force fresh install with no cache

# Optionally, you can add a label with the commit ID
LABEL commit_id=$COMMIT_ID

# Copy the rest of the application code
COPY . .

# Expose the port your app runs on
EXPOSE 3000

# Define the command to run your app
CMD ["yarn", "prod"]
