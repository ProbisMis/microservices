# Kafka Implementation Plan

## Overview

Integrate Apache Kafka into the microservices architecture to enable event-driven communication between services. This replaces/augments the current synchronous Feign client pattern with asynchronous messaging.

## Architecture Decision

**Approach**: Hybrid model - Kafka for async event-driven communication, keep Feign as optional fallback

**Kafka Mode**: KRaft (no Zookeeper) - simpler, production-ready since Kafka 3.3+

**Key Topics**:
| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `orders.placed` | order-service | inventory-service | Trigger inventory reservation |
| `inventory.reserved` | inventory-service | order-service | Confirm reservation success |
| `inventory.failed` | inventory-service | order-service | Notify reservation failure |
| `orders.confirmed` | order-service | (future consumers) | Order confirmation events |
| `products.events` | product-service | (future consumers) | Product CRUD events |

## Implementation Steps

### Phase 1: Infrastructure Setup

1. **Update `docker-compose.yml`** - Add Kafka service (KRaft mode) and Kafka UI
2. **Update parent `pom.xml`** - Add spring-kafka dependency management
3. **Create `events-common` module** - Shared event DTOs across services

### Phase 2: Order Service Changes

**Files to modify:**
- `order-service/pom.xml` - Add spring-kafka dependency
- `order-service/.../model/Order.java` - Add `status`, `failureReason`, `createdAt`, `updatedAt` fields
- `order-service/.../service/OrderService.java` - Add async order flow with event publishing
- `order-service/src/main/resources/application.properties` - Kafka config
- `order-service/src/main/resources/application-docker.properties` - Docker Kafka config

**Files to create:**
- `order-service/.../model/OrderStatus.java` - Enum: PENDING, CONFIRMED, REJECTED, FAILED
- `order-service/.../config/KafkaConfig.java` - Producer/consumer beans, topic creation
- `order-service/.../kafka/producer/OrderEventProducer.java` - Publish order events
- `order-service/.../kafka/consumer/InventoryEventConsumer.java` - Handle inventory responses

### Phase 3: Inventory Service Changes

**Files to modify:**
- `inventory-service/pom.xml` - Add spring-kafka dependency
- `inventory-service/.../model/Inventory.java` - Add `reservedQuantity` field
- `inventory-service/.../service/InventoryService.java` - Add reservation logic
- `inventory-service/src/main/resources/application.properties` - Kafka config
- `inventory-service/src/main/resources/application-docker.properties` - Docker Kafka config

**Files to create:**
- `inventory-service/.../model/InventoryReservation.java` - Track order reservations
- `inventory-service/.../repository/InventoryReservationRepository.java`
- `inventory-service/.../config/KafkaConfig.java` - Producer/consumer beans
- `inventory-service/.../kafka/producer/InventoryEventProducer.java` - Publish inventory events
- `inventory-service/.../kafka/consumer/OrderEventConsumer.java` - Handle order events

### Phase 4: Product Service Changes

**Files to modify:**
- `product-service/pom.xml` - Add spring-kafka dependency
- `product-service/.../service/ProductService.java` - Publish events on CRUD
- `product-service/src/main/resources/application.properties` - Kafka config

**Files to create:**
- `product-service/.../config/KafkaConfig.java` - Producer config
- `product-service/.../kafka/producer/ProductEventProducer.java` - Publish product events

### Phase 5: Testing

- Add `spring-kafka-test` dependency to services
- Create integration tests with `@EmbeddedKafka`
- Test async order flow end-to-end

## Key Files to Modify

| File | Changes |
|------|---------|
| `docker-compose.yml` | Add kafka, kafka-ui services; update service dependencies |
| `pom.xml` (parent) | Add spring-kafka to dependencyManagement |
| `order-service/pom.xml` | Add spring-kafka, events-common deps |
| `order-service/.../model/Order.java` | Add status, timestamps |
| `order-service/.../service/OrderService.java` | Async flow, event publishing |
| `inventory-service/pom.xml` | Add spring-kafka, events-common deps |
| `inventory-service/.../model/Inventory.java` | Add reservedQuantity |
| `inventory-service/.../service/InventoryService.java` | Reservation logic |
| `product-service/pom.xml` | Add spring-kafka deps |
| `product-service/.../service/ProductService.java` | Event publishing |

## New Order Flow

```
1. Client POST /api/order
2. Order saved with status=PENDING, return 202 Accepted
3. OrderPlacedEvent published to Kafka
4. Inventory service consumes event, reserves stock
5. InventoryReservedEvent (or FailedEvent) published
6. Order service consumes, updates status to CONFIRMED/REJECTED
```

## Docker Compose Addition

```yaml
# Kafka with KRaft (no Zookeeper)
kafka:
  image: confluentinc/cp-kafka:7.6.0
  container_name: kafka
  ports:
    - "9092:9092"
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
    KAFKA_LISTENERS: PLAINTEXT://kafka:29092,CONTROLLER://kafka:29093,PLAINTEXT_HOST://0.0.0.0:9092
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
    CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
  volumes:
    - kafka-data:/var/lib/kafka/data
  networks:
    - microservices-network
  healthcheck:
    test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server localhost:9092 || exit 1"]
    interval: 10s
    timeout: 10s
    retries: 10
    start_period: 30s

# Kafka UI for monitoring
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  container_name: kafka-ui
  ports:
    - "8090:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
  depends_on:
    kafka:
      condition: service_healthy
  networks:
    - microservices-network
```

## Verification Plan

1. **Start infrastructure**: `docker compose up -d kafka kafka-ui`
2. **Verify Kafka UI**: http://localhost:8090 - check topics created
3. **Start services**: `docker compose up -d --build`
4. **Test async order**:
   ```bash
   # Create product
   curl -X POST http://localhost:9000/api/product -H "Content-Type: application/json" \
     -d '{"name":"Test","description":"Test product","price":100}'

   # Add inventory
   curl -X POST http://localhost:9000/api/inventory -H "Content-Type: application/json" \
     -d '{"skuCode":"TEST-001","quantity":10}'

   # Place order (should return PENDING)
   curl -X POST http://localhost:9000/api/order -H "Content-Type: application/json" \
     -d '{"orderLineItemsDtoList":[{"skuCode":"TEST-001","price":100,"quantity":2}]}'

   # Check order status (should become CONFIRMED)
   curl http://localhost:9000/api/order/{orderNumber}
   ```
5. **Check Kafka UI**: Verify messages in topics
6. **Test failure case**: Order with insufficient inventory should become REJECTED
