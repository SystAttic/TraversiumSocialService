# SocialService

A microservice for managing comments and likes for user media. It integrates with other microservices in the ecosystem.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Service](#running-the-service)
- [API Documentation](#api-documentation)
- [Architecture](#architecture)
- [Database](#database)
- [Integration](#integration)
- [Monitoring and Health](#monitoring-and-health)

## Features

### Comments
- Comment creation and editing
- Creation of reply comments
- Comment deletion
- View comments and comment replies

### Likes
- Like and unlike media
- Get likes for media

### Security
- Firebase Authentication integration
- JWT token validation
- Multi-tenancy support
- Tenant isolation

### Integration
- REST API endpoints
- REST communication with TripService
- gRPC communication with ModerationService
- Kafka event streaming for notifications and audit logs
- Prometheus metrics for monitoring

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- Firebase project with service account credentials
- Kafka cluster (for event streaming)
- Docker (optional, for containerized deployment)

## Configuration

### Application Properties

The service is configured via `src/main/resources/application.properties`. Key configurations:

```properties
#Application
spring.application.name=SocialService
server.port=8083

#Database
spring.datasource.url=jdbc:postgresql://localhost:5432/social_tenants_db
spring.datasource.username=postgres
spring.datasource.password=postgres

#Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration/tenant

#Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.notification-topic=notification-topic
spring.kafka.audit-topic=audit-topic

#gRPC for moderation service
grpc.client.moderation-service.host=localhost
grpc.client.moderation-service.port=9090

#Config server (optional)
spring.config.import=optional:configserver:http://localhost:8888
```

### Kafka Configuration

Event streaming configuration for asynchronous communication:

- **`spring.kafka.bootstrap-servers`**: Kafka broker address for connecting to the Kafka cluster
- **`spring.kafka.notification-topic`**: Topic name for publishing notification events
- **`spring.kafka.audit-topic`**: Topic name for publishing audit events

### gRPC Configuration

gRPC client configuration for inter-service communication:

**ModerationService Client:**
- **`grpc.client.moderation-service.host`**: Hostname of the ModerationService gRPC server
- **`grpc.client.moderation-service.port`**: Port of the ModerationService gRPC server (content moderation for comments)

### Keycloak OAuth2 Configuration

Service-to-service authentication configuration for secure gRPC communication:

```properties
security.oauth2.client.token-uri=<issuer-url>
security.oauth2.client.client-id=social-service
security.oauth2.client.client-secret=<client-secret>
security.oauth2.client.grant-type=client_credentials
security.oauth2.client.refresh-skew-seconds=30
```

**Property Descriptions:**

- **`security.oauth2.client.token-uri`**: Keycloak endpoint URL for obtaining access tokens
    - Example: `http://localhost:8202/auth/realms/traversium/protocol/openid-connect/token`

- **`security.oauth2.client.client-id`**: Client ID registered in Keycloak for UserService

- **`security.oauth2.client.client-secret`**: Confidential client secret from Keycloak

- **`security.oauth2.client.grant-type`**: OAuth2 grant type for authentication
    - Use `client_credentials` for service-to-service (machine-to-machine) authentication

- **`security.oauth2.client.refresh-skew-seconds`**: Token refresh buffer time in seconds (default: 30)

## Running the Service

### Local Development

```bash
# Run with Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/SocialService-1.0.0.jar
```

### Using Docker

```bash
# Build Docker image
docker build -t traversium-social-service .

# Run container
docker run -p 8083:8083 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/social_tenants_db \
  traversium-social-service
```

### Verify Service is Running

```bash
# Health check
curl http://localhost:8083/actuator/health

# Liveness probe
curl http://localhost:8083/actuator/health/liveness

# Readiness probe
curl http://localhost:8083/actuator/health/readiness
```

## API Documentation

### REST API

Once the service is running, access the Swagger UI:

```
http://localhost:8083/swagger-ui.html
```

### Key Endpoints

**Comment Operations:**
- `POST /rest/v1/media/{mediaId}/comments` - Create comment
- `PUT /rest/v1/comments/{commentId}` - Edit comment
- `DELETE /rest/v1/comments/{commentId}` - Delete comment
- `GET /rest/v1/media/{mediaId}/comments` - Get comments for media
- `GET /rest/v1/comments/{commentId}/replies` - Get replies for comment
- `GET /rest/v1/comments/{commentId}` - Get individual comment

**Like Operations:**
- `POST /rest/v1/media/{mediaId}/likes` - Like media
- `DELETE /rest/v1/media/{mediaId}/likes` - Unlike media
- `GET /rest/v1/media/{mediaId}/likes` - Get likes for media
- `GET /rest/v1/media/{mediaId}/likes/check` - Check if user has liked media


## Architecture

### Multi-Tenancy

The service implements schema-based multi-tenancy using the `common-multitenancy` library. Each tenant has an isolated database schema.

### Security

- **Firebase Authentication**: All requests must include a valid Firebase ID token in the Authorization header
- **Tenant Filter**: Extracts and validates tenant context from request headers
- **Principal**: User context is available via `TraversiumPrincipal` in secured endpoints

### Event-Driven Architecture

The service publishes events to Kafka:
- **Notification Events**: Trigger notifications comment and like creation
- **Audit Events**: Track comment and like operations

## Database

### Schema Management

Database migrations are managed by Flyway. Migration scripts are located in:
```
src/main/resources/db/migration/tenant/
```

## Integration

### REST Clients

**TripService Client** (`src/main/kotlin/travesium/socialservice/service/ModerationServiceGrpcClient.kt`)
- Get owner of media
- Check if media exists

### gRPC Clients

**ModerationService Client** (`src/main/kotlin/travesium/socialservice/client/TripServiceClient.kt`):
- Content moderation for comments

### Kafka Integration

**Producers:**
- Notification events
- Audit events

**Configuration:** See `src/main/kotlin/travesium/socialservice/kafka/KafkaConfig.kt`

## Monitoring and Health

### Health Checks

- **Liveness**: `/actuator/health/liveness` - Indicates if the application is running
- **Readiness**: `/actuator/health/readiness` - Indicates if the application is ready to serve traffic
- **Database**: `/actuator/health/db` - Database connectivity check

### Metrics

Prometheus metrics exposed at:
```
http://localhost:8083/actuator/prometheus
```

Key metrics:
- JVM metrics (memory, threads, GC)
- HTTP request metrics
- Database connection pool metrics
- Circuit breaker state
- Custom business metrics

### Logging

Logs are structured in JSON format (Logstash encoder) for ELK Stack integration:
- Application logs: Log4j2
- Request/response logging
- Error tracking