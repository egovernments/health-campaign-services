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

# Remove heap limit: let Node.js use all available memory -- for now it is 1GB
ENV NODE_OPTIONS="--max-old-space-size=1024"

# Copy package.json and yarn.lock (if exists)
COPY package.json ./

# Install dependencies
RUN yarn install

# Optionally, you can add a label with the commit ID
LABEL commit_id=$COMMIT_ID

# Copy the rest of the application code
COPY . .

# Expose the port your app runs on
EXPOSE 3000

# Start the app
CMD ["yarn", "prod"]
