# Stage 1: Build the React application
FROM node:18.12.0-alpine AS builder

WORKDIR /app

# Set build arguments (passed from docker-compose)
ARG REACT_APP_PROD_BACKEND_PORT
ARG REACT_APP_PROD_SUPERSET_PORT
ARG REACT_APP_PROD_NGINX_PORT

# Persist values as environment variables
ENV REACT_APP_PROD_BACKEND_PORT=$REACT_APP_PROD_BACKEND_PORT
ENV REACT_APP_PROD_SUPERSET_PORT=$REACT_APP_PROD_SUPERSET_PORT
ENV REACT_APP_PROD_NGINX_PORT=$REACT_APP_PROD_NGINX_PORT

# Copy package files and install dependencies
COPY package*.json ./
RUN npm install

# Copy the rest of the application
COPY . .

ENV NODE_OPTIONS="--max-old-space-size=9000"

# Set environment variables for React at build time without exposing values
RUN echo "REACT_APP_PROD_BACKEND_PORT=\$REACT_APP_PROD_BACKEND_PORT" >> .env && \
    echo "REACT_APP_PROD_NGINX_PORT=\$REACT_APP_PROD_NGINX_PORT" >> .env && \
    echo "REACT_APP_PROD_SUPERSET_PORT=\$REACT_APP_PROD_SUPERSET_PORT" >> .env

# Build the React app
RUN npm run build

# Stage 2: Serve the built app with Nginx
FROM nginx:alpine

# Remove default Nginx configuration
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built React app from builder
COPY --from=builder /app/build /usr/share/nginx/html

CMD ["nginx", "-g", "daemon off;"]