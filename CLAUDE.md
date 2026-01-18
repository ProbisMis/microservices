# CLAUDE.md

Spring Boot 3.4.0 microservices with Java 21. E-commerce platform: product catalog, orders, inventory.

## Quick Start
```bash
docker compose up -d --build  # Start everything
mvn clean package -DskipTests  # Build all
```

## Architecture
```
Client → API Gateway:9000 → Services (via Eureka:8761)
         OAuth2 (Keycloak)     ↓
                         Order:8081 → Inventory:8082 (Feign)
```

| Service | Port | DB | Purpose |
|---------|------|----|---------|
| api-gateway | 9000 | - | Routing, OAuth2 |
| discovery-service | 8761 | - | Eureka registry |
| product-service | 8080 | MongoDB | Products CRUD |
| order-service | 8081 | MySQL | Orders (calls inventory) |
| inventory-service | 8082 | MySQL | Stock checks |

**Key Files**:
- `api-gateway/Routes.java` - routing config
- `api-gateway/SecurityConfig.java` - OAuth2 setup
- `order-service/client/InventoryClient.java` - Feign client

**Profiles**: `default` (localhost) / `docker` (container names)

## Commands
```bash
# Build
mvn clean package -pl <service> -DskipTests  # Single service
mvn clean package -pl order-service -am -DskipTests  # With deps

# Docker
docker compose up -d --build <service>  # Rebuild one
docker compose logs -f <service>
docker compose down -v  # Clean slate

# Test
mvn test -pl <service>
```

## API (via Gateway :9000)
```
Products:   POST/GET /api/product
Orders:     POST/GET /api/order
Inventory:  GET /api/inventory?skuCode=X
```

## Infrastructure

- **MongoDB**: localhost:27017, admin/password123
- **MySQL**: localhost:3306, root/mysql
- **Keycloak**: localhost:8181, admin/admin, realm: spring-microservices-security-realm

## Common Issues

- **Port conflict**: `lsof -ti:<port> | xargs kill -9`
- **Eureka registration fail**: Start discovery-service first, check `eureka.client.service-url`
- **DB connection**: Verify containers running: `docker compose ps`

## Adding New Service

1. Create module, add to parent POM
2. Set `spring.application.name` and Eureka config
3. Add route in `api-gateway/Routes.java`
4. Add to `docker-compose.yml`