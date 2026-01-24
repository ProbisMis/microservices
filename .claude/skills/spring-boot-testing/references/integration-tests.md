# Integration Test Patterns

## EmbeddedKafka Configuration

### Full Test Class Structure
```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "orders.placed",
        "inventory.reserved",
        "inventory.failed"
    },
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "auto.create.topics.enable=true"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.consumer.group-id=test-group-${random.uuid}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private EntityManager entityManager;

    private Producer<String, BaseEvent> producer;
    private Consumer<String, BaseEvent> consumer;

    @BeforeEach
    void setUp() {
        // Producer setup
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        producer = new DefaultKafkaProducerFactory<>(
            producerProps,
            new StringSerializer(),
            new JsonSerializer<BaseEvent>()
        ).createProducer();

        // Consumer setup
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            "test-consumer-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES,
            "com.demo.programming.events,com.demo.programming.events.*");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        consumer = new DefaultKafkaConsumerFactory<>(
            consumerProps,
            new StringDeserializer(),
            new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer();
        consumer.subscribe(List.of("inventory.reserved", "inventory.failed"));
    }

    @AfterEach
    void tearDown() {
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
        inventoryRepository.deleteAll();
    }
}
```

### Sending Test Events
```java
@Test
void whenOrderPlaced_shouldReserveInventory() throws Exception {
    // Given: inventory with sufficient stock
    Inventory inventory = new Inventory();
    inventory.setSkuCode("TEST-SKU-001");
    inventory.setQuantity(100);
    inventory.setReservedQuantity(0);
    inventoryRepository.save(inventory);

    String orderNumber = UUID.randomUUID().toString();

    // Wait for Spring consumer to be fully initialized
    Thread.sleep(3000);

    // When: send event
    OrderPlacedEvent event = OrderPlacedEvent.create(
        orderNumber,
        List.of(OrderLineItemEvent.builder()
            .skuCode("TEST-SKU-001")
            .price(BigDecimal.valueOf(50))
            .quantity(5)
            .build())
    );
    producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
    producer.flush();

    // Then: verify with Awaitility
    await().atMost(15, TimeUnit.SECONDS)
        .pollDelay(1, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> {
            entityManager.clear();
            Inventory updated = inventoryRepository.findBySkuCode("TEST-SKU-001");
            assertThat(updated.getReservedQuantity()).isEqualTo(5);
        });
}
```

### Consuming and Verifying Events
```java
// Verify event was published
ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
assertThat(records.count()).isGreaterThan(0);

boolean foundEvent = false;
for (var record : records) {
    if (record.value() instanceof InventoryReservedEvent reservedEvent) {
        if (reservedEvent.getOrderNumber().equals(orderNumber)) {
            foundEvent = true;
            break;
        }
    }
}
assertThat(foundEvent).isTrue();
```

### Creating Fresh Consumer Per Test
```java
@Test
void whenInventoryInsufficient_shouldPublishFailedEvent() throws Exception {
    // Use unique consumer group to avoid interference
    Map<String, Object> freshConsumerProps = KafkaTestUtils.consumerProps(
        "failed-test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
    freshConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    freshConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES,
        "com.demo.programming.events,com.demo.programming.events.*");

    try (Consumer<String, BaseEvent> failedConsumer = new DefaultKafkaConsumerFactory<>(
            freshConsumerProps,
            new StringDeserializer(),
            new JsonDeserializer<>(BaseEvent.class, false)
    ).createConsumer()) {
        failedConsumer.subscribe(List.of("inventory.failed"));

        // Test logic here...

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, BaseEvent> records =
                KafkaTestUtils.getRecords(failedConsumer, Duration.ofSeconds(5));
            // Verify records...
        });
    }
}
```

## Awaitility Async Assertions

### Basic Patterns
```java
// Wait with timeout and poll interval
await().atMost(15, TimeUnit.SECONDS)
    .pollInterval(500, TimeUnit.MILLISECONDS)
    .untilAsserted(() -> {
        assertThat(repository.count()).isEqualTo(5);
    });

// With poll delay (wait before first check)
await().atMost(30, TimeUnit.SECONDS)
    .pollDelay(2, TimeUnit.SECONDS)
    .pollInterval(500, TimeUnit.MILLISECONDS)
    .untilAsserted(() -> {
        Order updated = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    });

// Wait until condition is true
await().atMost(10, TimeUnit.SECONDS)
    .until(() -> repository.findByOrderNumber(orderNumber).isPresent());
```

### With Custom Description
```java
await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
    assertThat(foundFailedEvent)
        .as("Expected InventoryReservationFailedEvent for order %s", orderNumber)
        .isTrue();
});
```

## EntityManager Cache Clearing

When testing JPA entities modified by async processes:

```java
@Autowired
private EntityManager entityManager;

@Test
void whenEventProcessed_shouldUpdateDatabase() {
    // ... trigger async processing ...

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
        // CRITICAL: Clear JPA cache to see changes from other transactions
        entityManager.clear();

        Inventory updated = inventoryRepository.findBySkuCode(skuCode);
        assertThat(updated.getReservedQuantity()).isEqualTo(5);
    });
}
```

## Test Isolation with @DirtiesContext

```java
// Reset context after each test (recommended for Kafka tests)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

// Reset context after all tests in class
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
```

## TestContainers for MongoDB/MySQL

### MongoDB
```java
@Testcontainers
@SpringBootTest
class ProductServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
}
```

### MySQL
```java
@Testcontainers
@SpringBootTest
class OrderServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }
}
```

## Required Imports

```java
// Kafka Test
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

// Spring Test
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

// Awaitility
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

// JPA
import jakarta.persistence.EntityManager;
```
