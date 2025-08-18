# Build stage
FROM golang:1.24-alpine AS builder

WORKDIR /app

# Copy go.mod and go.sum files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy the source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -o digit-abdm ./cmd/server

# Final stage
FROM alpine:3.19

WORKDIR /app

# Copy the binary from the builder stage
COPY --from=builder /app/digit-abdm .

# Copy migrations
COPY migrations ./migrations

# Set the entry point
ENTRYPOINT ["./digit-abdm"]

# Expose the port
EXPOSE 8080