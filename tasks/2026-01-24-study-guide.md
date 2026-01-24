# Spring Boot Study Guide - January 24, 2026

## Your Project Context
Your microservices project already implements:
- **Request Binding**: `@RequestParam`, `@PathVariable`, `@RequestBody` in all controllers
- **Transaction Management**: `@Transactional` with `readOnly` variants
- **Logging**: `@Slf4j` throughout services
- **OpenAPI**: Swagger documentation with annotations

**Missing patterns** (good learning opportunities):
- Global exception handling (`@RestControllerAdvice`)
- Input validation (`@Valid`, JSR-303)
- Resilience patterns (Circuit Breaker, Retry)
- Distributed tracing (Micrometer)

---

# Morning Session: Core Architecture & Web Layer

## 1. @RestControllerAdvice - Global Exception Handling

### Theory
`@RestControllerAdvice` combines `@ControllerAdvice` + `@ResponseBody` to create a centralized exception handler for ALL controllers.

**Why use it?**
- Single place to handle all exceptions
- Consistent error response format across your API
- Separates exception handling from business logic
- Better for microservices (standardized error contracts)

### Your Current State
Your services throw exceptions inline:
```java
// order-service/service/OrderService.java:58
throw new IllegalArgumentException("Product is not in stock, please try again later");

// order-service/service/OrderService.java:95
throw new IllegalArgumentException("Order not found: " + orderNumber);
```

These become HTTP 500 errors with ugly stack traces - not ideal for API consumers.

### Solution: Create Global Exception Handler

```java
// order-service/src/main/java/com/demo/programming/order_service/exception/GlobalExceptionHandler.java

package com.demo.programming.order_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Standard error response structure
    record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
    ) {}

    // Handle business logic exceptions (e.g., "out of stock")
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            jakarta.servlet.http.HttpServletRequest request) {

        log.warn("Business rule violation: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    // Handle validation errors (from @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("errors", fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            jakarta.servlet.http.HttpServletRequest request) {

        log.error("Unexpected error", ex);

        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred",  // Don't expose internal details
            request.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(error);
    }
}
```

### When to use local vs global exception handling

| Use Case | Approach |
|----------|----------|
| Business validation (out of stock, not found) | Global `@RestControllerAdvice` |
| Input validation (`@Valid`) | Global `@RestControllerAdvice` |
| Controller-specific error (rare) | Local `@ExceptionHandler` in controller |
| Third-party API errors | Global handler with specific exception class |

---

## 2. @Valid + JSR-303 Validation

### Theory
Jakarta Bean Validation (formerly JSR-303) provides declarative validation through annotations.

**Key annotations:**
- `@NotNull` - Field cannot be null
- `@NotBlank` - String cannot be null, empty, or whitespace
- `@NotEmpty` - Collection/array cannot be null or empty
- `@Size(min, max)` - String/collection size constraints
- `@Min`, `@Max` - Numeric bounds
- `@Pattern(regexp)` - Regex validation
- `@Email` - Email format
- `@Positive`, `@PositiveOrZero` - Numeric sign constraints

### Your Current State
Your DTOs lack validation:
```java
// order-service/dto/OrderLineItemsDto.java
public class OrderLineItemsDto {
    private Long id;
    private String skuCode;    // Could be null or empty!
    private Double price;      // Could be negative!
    private Integer quantity;  // Could be zero or negative!
}
```

### Solution: Add Validation Annotations

```java
// order-service/dto/OrderLineItemsDto.java (enhanced)

package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Details of an order line item")
public class OrderLineItemsDto {

    @Schema(description = "Unique identifier of the line item", example = "1")
    private Long id;

    @NotBlank(message = "SKU code is required")
    @Size(min = 3, max = 50, message = "SKU code must be between 3 and 50 characters")
    @Schema(description = "Stock Keeping Unit code of the product", example = "IPHONE_15_PRO")
    private String skuCode;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Schema(description = "Price of the product", example = "999.99")
    private Double price;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity cannot exceed 1000")
    @Schema(description = "Quantity ordered", example = "2")
    private Integer quantity;
}
```

```java
// order-service/dto/OrderRequest.java (enhanced)

package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for placing a new order")
public class OrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid  // Cascades validation to nested objects
    @Schema(description = "List of order line items")
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
```

```java
// Controller: Add @Valid to trigger validation
@PostMapping
public String placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
    orderService.placeOrder(orderRequest);
    return "Order placed successfully!";
}
```

**Note:** Spring Boot 3.x uses `jakarta.validation` package (not `javax.validation`).

---

## 3. @PostConstruct - Lifecycle Hooks

### Theory
`@PostConstruct` runs AFTER:
1. Bean instantiation
2. Dependency injection completes
3. But BEFORE the bean is put into service

**Use cases:**
- Initialize resources (database connections, caches)
- Validate configuration
- Warm up caches
- Register with external services

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private Map<String, BigDecimal> productPriceCache;

    @PostConstruct
    public void init() {
        // Initialize cache after dependencies are injected
        productPriceCache = new ConcurrentHashMap<>();
        log.info("OrderService initialized, cache warmed up");
    }

    @PreDestroy
    public void cleanup() {
        // Cleanup resources before bean destruction
        productPriceCache.clear();
        log.info("OrderService cleaning up");
    }
}
```

**Spring Boot alternative:** Use `ApplicationRunner` or `CommandLineRunner` for app-level initialization.

---

## 4. Request Binding Patterns

### Your Existing Examples

**@RequestParam** - Query parameters (`/api?id=1`):
```java
// inventory-service/controller/InventoryController.java:45
@GetMapping("/check")
public List<InventoryResponse> isInStock(
    @RequestParam("sku-code") List<String> skuCode) {  // /check?sku-code=A&sku-code=B
    return inventoryService.isInStock(skuCode);
}
```

**@PathVariable** - URL segments (`/api/users/{id}`):
```java
// order-service/controller/OrderController.java:65
@GetMapping("/{orderNumber}")
public OrderResponse getOrderByOrderNumber(
    @PathVariable String orderNumber) {  // /order/abc-123
    return orderService.getOrderByOrderNumber(orderNumber);
}
```

**@RequestBody** - JSON payloads:
```java
// order-service/controller/OrderController.java:41
@PostMapping
public String placeOrder(@RequestBody OrderRequest orderRequest) {
    orderService.placeOrder(orderRequest);
    return "Order placed successfully!";
}
```

### Advanced Patterns

```java
// Optional request param with default
@GetMapping("/search")
public List<Product> search(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(required = false) String category) {
    //...
}

// Multiple path variables
@GetMapping("/users/{userId}/orders/{orderId}")
public Order getOrder(
    @PathVariable Long userId,
    @PathVariable Long orderId) {
    //...
}

// Matrix variables (rarely used)
// /products;category=electronics;brand=apple
@GetMapping("/products/{filters}")
public List<Product> getFiltered(
    @MatrixVariable Map<String, String> filters) {
    //...
}
```

---

## 5. Thread Safety - ConcurrentHashMap

### Interview-Ready Explanation

**ConcurrentHashMap** is the go-to thread-safe Map implementation. Key characteristics:

| Feature | HashMap | Hashtable | ConcurrentHashMap |
|---------|---------|-----------|-------------------|
| Thread-safe | No | Yes (synchronized) | Yes (segmented) |
| Null keys/values | Yes | No | No |
| Lock granularity | N/A | Whole table | Per-segment/bucket |
| Read performance | O(1) | Blocked by writes | O(1), lock-free |

### Internals (Java 8+)

```
ConcurrentHashMap Structure:
┌─────────────────────────────────────────────────┐
│  Bucket Array (Node<K,V>[])                     │
├─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┤
│  0  │  1  │  2  │  3  │  4  │  5  │  6  │  7  │
├─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┤
│  Each bucket: Linked list OR Tree (>8 nodes)   │
│  Write: CAS (Compare-And-Swap) or synchronized │
│  Read: Volatile reads (lock-free)              │
└─────────────────────────────────────────────────┘
```

**Key mechanisms:**
1. **Lock-free reads**: `volatile` Node.val ensures visibility
2. **Fine-grained writes**: Locks only the affected bucket
3. **CAS operations**: Atomic updates without locks
4. **Tree bins**: When bucket has >8 entries, converts to red-black tree (O(log n))

### Common Patterns

```java
// WRONG: Check-then-act race condition
if (!map.containsKey(key)) {
    map.put(key, value);  // Another thread might put between check and put
}

// RIGHT: Atomic operations
map.putIfAbsent(key, value);
map.computeIfAbsent(key, k -> expensiveComputation(k));
map.compute(key, (k, v) -> v == null ? 1 : v + 1);  // Atomic increment
map.merge(key, 1, Integer::sum);  // Also atomic increment
```

---

# Afternoon Session: Resilience & Observability

## 6. Resilience4j Patterns

### Theory

Resilience4j is a lightweight fault tolerance library. Key patterns:

```
┌─────────────────────────────────────────────────────────┐
│                    Circuit Breaker States               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   CLOSED ──────────> OPEN ──────────> HALF_OPEN        │
│     ↑    (failures     │    (wait         │            │
│     │     exceed       │     period)      │            │
│     │     threshold)   │                  ▼            │
│     │                  │            Test request       │
│     │                  │            succeeds?          │
│     │                  │              ↙   ↘           │
│     └──────────────────┴──── Yes ──┘       No ────→ OPEN
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Adding Resilience4j to Your Project

**1. Add dependency (pom.xml):**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

**2. Configure (application.yml):**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3

  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException

  bulkhead:
    instances:
      inventoryService:
        maxConcurrentCalls: 10
        maxWaitDuration: 100ms
```

**3. Apply to your Feign client:**
```java
// order-service/client/InventoryClient.java (enhanced)

package com.demo.programming.order_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

@FeignClient(name = "inventory-service", url = "${inventory.service.url:}")
public interface InventoryClient {

    @GetMapping("/api/inventory/check")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "isInStockFallback")
    @Retry(name = "inventoryService")
    List<InventoryResponse> isInStock(@RequestParam("sku-code") List<String> skuCode);

    // Fallback when circuit is open or all retries exhausted
    default List<InventoryResponse> isInStockFallback(List<String> skuCode, Throwable t) {
        log.warn("Fallback triggered for inventory check. Reason: {}", t.getMessage());
        // Conservative response - assume out of stock
        return skuCode.stream()
            .map(sku -> new InventoryResponse(sku, false))
            .toList();
    }
}
```

### Pattern Comparison

| Pattern | Purpose | When to Use |
|---------|---------|-------------|
| **Circuit Breaker** | Stop calling failing service | Downstream service is unhealthy |
| **Retry** | Retry transient failures | Network hiccups, timeouts |
| **Bulkhead** | Limit concurrent calls | Prevent resource exhaustion |
| **Rate Limiter** | Limit call frequency | Protect from overload |
| **Time Limiter** | Set timeout | Avoid hanging calls |

### Resilience4j vs Service Mesh

| Aspect | Resilience4j | Service Mesh (Istio) |
|--------|--------------|----------------------|
| Implementation | Application code | Infrastructure |
| Language | Java only | Any language |
| Configuration | Code/YAML | Kubernetes CRDs |
| Visibility | App metrics | Network-level |
| Complexity | Low | High |
| Use case | Single app | Platform-wide |

**Interview answer:** "Resilience4j is ideal for Java microservices where you need fine-grained control. Service mesh is better for polyglot environments where you want platform-level resilience policies without changing application code."

---

## 7. Observability - LGTM Stack

### The Three Pillars

```
┌─────────────────────────────────────────────────────────┐
│                    Observability                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   LOGS          METRICS          TRACES                 │
│   (Loki)       (Prometheus)      (Tempo)                │
│                                                         │
│   "What         "How much        "Where did             │
│    happened?"    is happening?"   time go?"             │
│                                                         │
│   Debug info    CPU, memory,     Request flow          │
│   Errors        latency, rate    across services       │
│   Events        Error counts     Bottlenecks           │
│                                                         │
└─────────────────────────────────────────────────────────┘
              │
              ▼
        ┌───────────┐
        │  Grafana  │  ← Unified dashboard
        └───────────┘
```

### Trace ID Propagation

```
┌──────────┐      ┌──────────┐      ┌──────────┐
│  Client  │─────>│  Order   │─────>│Inventory │
│          │      │ Service  │      │ Service  │
└──────────┘      └──────────┘      └──────────┘
                        │                 │
                        │   traceparent:  │
                        │   00-abc123...  │
                        └────────┬────────┘
                                 │
              All logs, metrics, spans share
              the same trace ID: abc123
```

**Headers used:**
- **W3C Trace Context**: `traceparent`, `tracestate` (modern standard)
- **B3 (Zipkin)**: `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-ParentSpanId`

### Adding Micrometer Tracing to Your Project

**1. Add dependencies (pom.xml):**
```xml
<!-- Micrometer core -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin reporter (or use OTLP for Tempo) -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>

<!-- Actuator for metrics endpoint -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**2. Configure (application.yml):**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, lower in prod
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**3. Custom metrics with @Timed:**
```java
import io.micrometer.core.annotation.Timed;

@Service
public class OrderService {

    @Timed(value = "order.placement", description = "Time to place an order")
    public void placeOrder(OrderRequest orderRequest) {
        // Business logic
    }
}
```

---

# Evening Session: Modern Spring Boot

## 8. Virtual Threads (Project Loom)

### Theory

Traditional threads:
- OS-managed, ~1MB stack each
- Context switching is expensive
- Limited to thousands per JVM

Virtual threads (Java 21+):
- JVM-managed, lightweight
- Millions possible per JVM
- Ideal for I/O-bound work

```
┌─────────────────────────────────────────────────────────┐
│                  Traditional Threads                    │
├─────────────────────────────────────────────────────────┤
│  Thread 1 ──────[HTTP Request]─────────────────> Done   │
│  Thread 2 ──────────────[DB Query]─────────────> Done   │
│  Thread 3 ──[Feign Call]───────────────────────> Done   │
│                                                         │
│  = 3 OS threads consumed, even during I/O waiting       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                   Virtual Threads                       │
├─────────────────────────────────────────────────────────┤
│  VT 1 ──[work]──|park|──[work]──> Done                 │
│  VT 2 ──────[work]──|park|──> Done                     │
│  VT 3 ──[work]──> Done                                  │
│                                                         │
│  Carrier Thread Pool: Just 2 OS threads handle all 3   │
│  (Virtual threads unmount during I/O blocking)         │
└─────────────────────────────────────────────────────────┘
```

### Enable in Spring Boot 3.2+

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

That's it! Spring Boot automatically uses virtual threads for:
- Tomcat request handling
- Async task execution
- Scheduled tasks

### When NOT to use Virtual Threads

- CPU-bound computation (use regular threads + work-stealing)
- Synchronized blocks that hold locks for long periods
- Libraries with thread-local assumptions

---

## 9. Spring Data JPA Optimization - N+1 Problem

### The Problem

```java
// Entity relationship
@Entity
public class Order {
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderLineItems> items;
}

// Query that triggers N+1
List<Order> orders = orderRepository.findAll();  // 1 query
for (Order order : orders) {
    order.getItems().size();  // N additional queries!
}
```

**Result:** 1 + N queries (1 for orders, N for each order's items)

### Solution 1: JOIN FETCH

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
    List<Order> findByStatusWithItems(@Param("status") OrderStatus status);
}
```

### Solution 2: @EntityGraph

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByStatus(OrderStatus status);
}

// Or define named entity graph
@Entity
@NamedEntityGraph(
    name = "Order.withItems",
    attributeNodes = @NamedAttributeNode("items")
)
public class Order { ... }

// Use in repository
@EntityGraph("Order.withItems")
List<Order> findByStatus(OrderStatus status);
```

### Detecting N+1 Issues

Add to application.yml for development:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
logging:
  level:
    org.hibernate.stat: DEBUG
    org.hibernate.SQL: DEBUG
```

---

## 10. Containerization - Buildpacks

### Theory

**Buildpacks** create OCI-compliant container images without a Dockerfile:
- Analyzes your source code
- Selects appropriate base image
- Applies security patches automatically
- Optimizes layer caching

### Usage

```bash
# Build image directly
./mvnw spring-boot:build-image -pl order-service

# Or configure in pom.xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <name>myregistry/order-service:${project.version}</name>
            <env>
                <BP_JVM_VERSION>21</BP_JVM_VERSION>
            </env>
        </image>
    </configuration>
</plugin>
```

### Buildpacks vs Dockerfile

| Aspect | Buildpacks | Dockerfile |
|--------|------------|------------|
| Learning curve | Low | Medium |
| Control | Less | Full |
| Security updates | Automatic | Manual |
| Reproducibility | High | Depends on author |
| Build time | Longer (first time) | Faster |

---

# Practical Exercises

## Exercise 1: Add Global Exception Handler

Create `GlobalExceptionHandler.java` in order-service using the template from Section 1.

## Exercise 2: Add Validation

1. Add jakarta.validation dependency to pom.xml
2. Annotate OrderLineItemsDto with validation constraints
3. Add @Valid to controller methods
4. Test with invalid input

## Exercise 3: Add Resilience4j

1. Add resilience4j-spring-boot3 dependency
2. Configure circuit breaker in application.yml
3. Annotate InventoryClient with @CircuitBreaker
4. Implement fallback method
5. Test by stopping inventory-service

---

# Verification Checklist

- [ ] Can explain Circuit Breaker states (Closed → Open → Half-Open)
- [ ] Know when to use @RestControllerAdvice vs local @ExceptionHandler
- [ ] Understand Trace ID propagation through B3/W3C headers
- [ ] Can explain ConcurrentHashMap's lock-free reads
- [ ] Know how to fix N+1 with JOIN FETCH or @EntityGraph
- [ ] Can explain Resilience4j vs Service Mesh trade-offs

---

# Interview Q&A Quick Reference

**Q: When should I use Resilience4j vs a Service Mesh?**
A: Resilience4j for fine-grained Java control; Service Mesh for platform-wide polyglot policies.

**Q: How does ConcurrentHashMap achieve thread safety without full synchronization?**
A: Lock-free volatile reads, per-bucket synchronized writes, and CAS operations for atomic updates.

**Q: What's the difference between @Valid and @Validated?**
A: @Valid is JSR-303 standard (works on method params/fields), @Validated is Spring-specific (supports groups).

**Q: How does a Trace ID propagate between microservices?**
A: Via HTTP headers (W3C traceparent or B3 headers). Instrumentation libraries read incoming headers and include them in outgoing requests.

**Q: What's the N+1 problem and how do you fix it?**
A: Lazy-loaded relationships trigger extra queries for each parent. Fix with JOIN FETCH or @EntityGraph to eagerly load in one query.
