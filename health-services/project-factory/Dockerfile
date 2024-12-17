# Use Node.js base image with version 16
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

# Copy package.json and yarn.lock (if exists)
COPY package.json ./

# Install dependencies
RUN yarn cache clean --force && yarn install --force

# Optionally, you can add a label with the commit ID
LABEL commit_id=$COMMIT_ID

# Copy the rest of the application code
COPY . .

# Expose the port your app runs on
EXPOSE 3000

CMD ["yarn", "prod"]
# Replaced by CMD ["yarn", "prod"]