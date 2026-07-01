# Use a maintained Node 20 LTS runtime (node:14 is EOL)
FROM node:20-alpine

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install any dependencies
RUN npm install

# Bundle your app's source code inside the Docker image
COPY . .

# Set server port
ENV SERVER_PORT 8080

# Run as the built-in non-root 'node' user
USER node

# Define the command to run your app using CMD which defines your runtime
CMD ["npm", "start"]
