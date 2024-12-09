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

# Set the DEBUG environment variable (default to "false")
ENV DEBUG=false

# Copy package.json and yarn.lock (if exists)
COPY package.json ./

# Install dependencies
RUN yarn install

# Optionally, you can add a label with the commit ID
LABEL commit_id=$COMMIT_ID

# Copy the rest of the application code
COPY . .

# Copy the correct tsconfig.json based on the DEBUG environment variable
RUN if [ "$DEBUG" = "true" ]; then cp tsconfig.debug.json tsconfig.json; else cp tsconfig.json tsconfig.json; fi

# Expose the port your app runs on and the debugging port
EXPOSE 3000

# Run the application based on the DEBUG environment variable
CMD ["sh", "-c", "if [ \"$DEBUG\" = \"true\" ]; then yarn inspect; else yarn prod; fi"]