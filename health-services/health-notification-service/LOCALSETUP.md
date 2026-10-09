# Local Setup Guide

## Prerequisites
Ensure the following are installed and running:
- Java 17
- Maven 3.6+
- PostgreSQL 12+
- Kafka 2.8+

## Database Setup

1. Create PostgreSQL database:
```sql
CREATE DATABASE postgres;
```

2. Update database credentials in `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=postgres
```

## Kafka Setup

1. Start Zookeeper:
```bash
bin/zookeeper-server-start.sh config/zookeeper.properties
```

2. Start Kafka:
```bash
bin/kafka-server-start.sh config/server.properties
```

3. Create topics:
```bash
bin/kafka-topics.sh --create --topic health-notification-consumer-topic --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic health-notification-producer-topic --bootstrap-server localhost:9092
```

## Build and Run

1. Build the project:
```bash
mvn clean install
```

2. Run the application:
```bash
mvn spring-boot:run
```

Or run with specific profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Verify Setup

The application should start on port 8080. Check:
```bash
curl http://localhost:8080/health-notification-service/actuator/health
```

## Database Migrations

Flyway will automatically run migrations on startup. Migrations are located in:
`src/main/resources/db/migration/main/`

To check migration status:
```bash
mvn flyway:info
```

## Common Issues

### Port Already in Use
Change the port in application.properties:
```properties
server.port=8081
```

### Database Connection Failed
- Verify PostgreSQL is running
- Check credentials in application.properties
- Ensure database exists

### Kafka Connection Failed
- Verify Kafka and Zookeeper are running
- Check bootstrap server configuration
- Ensure topics are created
