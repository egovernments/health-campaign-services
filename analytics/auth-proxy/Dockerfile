# Use an official Node runtime as a parent image
FROM node:25@sha256:78839ac448c23517f8eab2e8f7943d9b4f73979eb7f8bed2c73dbf72ff869e7b

ARG WORK_DIR

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy source code first
COPY ${WORK_DIR}/. .

# Copy package.json and package-lock.json
COPY package*.json ./

# Install any dependencies
RUN npm install

# Set server port
ENV SERVER_PORT=8080

# Define the command to run your app using CMD which defines your runtime
CMD ["npm", "start"]
