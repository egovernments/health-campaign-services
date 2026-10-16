# Use an official Node runtime as a parent image
FROM node:14@sha256:a158d3b9b4e3fa813fa6c8c590b8f0a860e015ad4e59bbce5744d2f6fd8461aa

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
