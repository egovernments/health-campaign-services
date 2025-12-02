FROM nginx:alpine

# Install envsubst (provided by gettext)
RUN apk add --no-cache gettext

# Remove the default configuration
RUN rm /etc/nginx/conf.d/default.conf

# Copy the Nginx configuration template
COPY nginx.conf.template /etc/nginx/conf.d/

# Use envsubst to replace only the specified environment variables and start Nginx
CMD ["sh", "-c", "envsubst '$FRONTEND_HOST $FRONTEND_PORT $BACKEND_HOST $BACKEND_PORT' < /etc/nginx/conf.d/nginx.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
