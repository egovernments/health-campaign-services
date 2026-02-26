# Health Notification Service

## Overview
The Health Notification Service is a Spring Boot microservice designed to handle notification processing for the health campaign services platform.

## Features
- Kafka-based event-driven architecture
- PostgreSQL database with Flyway migrations
- Multi-tenancy support with central instance handling
- Integration with health-services-common library

## Tech Stack
- Java 17
- Spring Boot 3.2.2
- PostgreSQL
- Kafka
- Flyway

## Prerequisites
- JDK 17
- PostgreSQL 12+
- Kafka
- Maven 3.6+

## Configuration
Main configuration is in `src/main/resources/application.properties`

### Key Properties
- `server.port` - Service port (default: 8080)
- `spring.datasource.url` - PostgreSQL connection URL
- `kafka.config.bootstrap_server_config` - Kafka broker URL
- `spring.redis.host` - Redis host

## Build
```bash
mvn clean install
```

## Run
```bash
mvn spring-boot:run
```

## Database Migrations
Flyway migrations are located in `src/main/resources/db/migration/main/`

## API Documentation
Service exposes REST APIs at context path: `/health-notification-service`

## Project Structure
```
src/main/java/org/egov/healthnotification/
├── config/              # Configuration classes
├── consumer/            # Kafka consumers
├── producer/            # Kafka producers
├── service/             # Business logic
├── repository/          # Data access layer
├── web/                 # REST controllers and models
├── util/                # Utility classes
└── validators/          # Validation logic
```

## Development
To start development:
1. Configure database connection in application.properties
2. Add your domain models
3. Create repository, service, and controller layers
4. Add Kafka consumers/producers as needed
5. Implement business logic

## Testing
Run tests with:
```bash
mvn test
```
