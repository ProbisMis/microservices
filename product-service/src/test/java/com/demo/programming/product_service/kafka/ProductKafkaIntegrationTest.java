package com.demo.programming.product_service.kafka;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.product.ProductCreatedEvent;
import com.demo.programming.events.product.ProductDeletedEvent;
import com.demo.programming.events.product.ProductUpdatedEvent;
import com.demo.programming.product_service.dto.ProductRequest;
import com.demo.programming.product_service.model.Product;
import com.demo.programming.product_service.repository.ProductRepository;
import com.demo.programming.product_service.service.ProductService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"products.events"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "auto.create.topics.enable=true"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "eureka.client.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductKafkaIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Consumer<String, BaseEvent> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("product-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer();
        consumer.subscribe(Collections.singletonList("products.events"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        productRepository.deleteAll();
    }

    @Test
    void whenProductCreated_shouldPublishProductCreatedEvent() throws Exception {
        // Given
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .build();

        // Wait for Kafka consumer to be fully initialized
        Thread.sleep(2000);

        // When
        var response = productService.createProduct(request);

        // Then: ProductCreatedEvent should be published
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
                    assertThat(records.count()).isGreaterThan(0);

                    boolean foundEvent = false;
                    for (var record : records) {
                        if (record.value() instanceof ProductCreatedEvent event) {
                            if (event.getProductId().equals(response.getId())) {
                                foundEvent = true;
                                assertThat(event.getName()).isEqualTo("Test Product");
                                assertThat(event.getDescription()).isEqualTo("Test Description");
                                assertThat(event.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
                                break;
                            }
                        }
                    }
                    assertThat(foundEvent)
                            .as("Expected ProductCreatedEvent for product %s", response.getId())
                            .isTrue();
                });
    }

    @Test
    void whenProductUpdated_shouldPublishProductUpdatedEvent() throws Exception {
        // Given: an existing product
        Product product = Product.builder()
                .name("Original Product")
                .description("Original Description")
                .price(BigDecimal.valueOf(50.00))
                .build();
        Product savedProduct = productRepository.save(product);

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Product")
                .description("Updated Description")
                .price(BigDecimal.valueOf(75.00))
                .build();

        // Wait for Kafka consumer to be fully initialized
        Thread.sleep(2000);

        // When
        productService.updateProduct(savedProduct.getId(), updateRequest);

        // Then: ProductUpdatedEvent should be published
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
                    assertThat(records.count()).isGreaterThan(0);

                    boolean foundEvent = false;
                    for (var record : records) {
                        if (record.value() instanceof ProductUpdatedEvent event) {
                            if (event.getProductId().equals(savedProduct.getId())) {
                                foundEvent = true;
                                assertThat(event.getName()).isEqualTo("Updated Product");
                                assertThat(event.getDescription()).isEqualTo("Updated Description");
                                assertThat(event.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(75.00));
                                break;
                            }
                        }
                    }
                    assertThat(foundEvent)
                            .as("Expected ProductUpdatedEvent for product %s", savedProduct.getId())
                            .isTrue();
                });
    }

    @Test
    void whenProductDeleted_shouldPublishProductDeletedEvent() throws Exception {
        // Given: an existing product
        Product product = Product.builder()
                .name("Product To Delete")
                .description("Will be deleted")
                .price(BigDecimal.valueOf(25.00))
                .build();
        Product savedProduct = productRepository.save(product);

        // Wait for Kafka consumer to be fully initialized
        Thread.sleep(2000);

        // When
        productService.deleteProduct(savedProduct.getId());

        // Then: ProductDeletedEvent should be published
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
                    assertThat(records.count()).isGreaterThan(0);

                    boolean foundEvent = false;
                    for (var record : records) {
                        if (record.value() instanceof ProductDeletedEvent event) {
                            if (event.getProductId().equals(savedProduct.getId())) {
                                foundEvent = true;
                                break;
                            }
                        }
                    }
                    assertThat(foundEvent)
                            .as("Expected ProductDeletedEvent for product %s", savedProduct.getId())
                            .isTrue();
                });
    }

    @Test
    void productEvents_shouldContainCorrectFields() throws Exception {
        // Given
        ProductRequest request = ProductRequest.builder()
                .name("Field Test Product")
                .description("Testing all event fields")
                .price(BigDecimal.valueOf(123.45))
                .build();

        // Wait for Kafka consumer to be fully initialized
        Thread.sleep(2000);

        // When
        var response = productService.createProduct(request);

        // Then: Event should contain all base event fields plus product fields
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
                    assertThat(records.count()).isGreaterThan(0);

                    ProductCreatedEvent foundEvent = null;
                    for (var record : records) {
                        if (record.value() instanceof ProductCreatedEvent event) {
                            if (event.getProductId().equals(response.getId())) {
                                foundEvent = event;
                                break;
                            }
                        }
                    }
                    assertThat(foundEvent).isNotNull();

                    // Verify base event fields
                    assertThat(foundEvent.getEventId()).isNotNull();
                    assertThat(foundEvent.getEventType()).isEqualTo("PRODUCT_CREATED");
                    assertThat(foundEvent.getTimestamp()).isNotNull();
                    assertThat(foundEvent.getCorrelationId()).isEqualTo(response.getId());

                    // Verify product-specific fields
                    assertThat(foundEvent.getProductId()).isEqualTo(response.getId());
                    assertThat(foundEvent.getName()).isEqualTo("Field Test Product");
                    assertThat(foundEvent.getDescription()).isEqualTo("Testing all event fields");
                    assertThat(foundEvent.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(123.45));
                });
    }
}
