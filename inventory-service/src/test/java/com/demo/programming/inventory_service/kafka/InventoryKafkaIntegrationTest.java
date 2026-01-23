package com.demo.programming.inventory_service.kafka;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.inventory.InventoryReservationFailedEvent;
import com.demo.programming.events.inventory.InventoryReservedEvent;
import com.demo.programming.events.order.OrderLineItemEvent;
import com.demo.programming.events.order.OrderPlacedEvent;
import com.demo.programming.inventory_service.model.Inventory;
import com.demo.programming.inventory_service.model.InventoryReservation;
import com.demo.programming.inventory_service.repository.InventoryRepository;
import com.demo.programming.inventory_service.repository.InventoryReservationRepository;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private EntityManager entityManager;

    private Producer<String, BaseEvent> producer;
    private Consumer<String, BaseEvent> consumer;

    @BeforeEach
    void setUp() {
        // Set up producer for sending order events
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        producer = new DefaultKafkaProducerFactory<>(
                producerProps,
                new StringSerializer(),
                new JsonSerializer<BaseEvent>()
        ).createProducer();

        // Set up consumer for verifying inventory events
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-consumer-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
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
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
        inventoryReservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void whenOrderPlacedWithSufficientInventory_shouldReserveAndPublishReservedEvent() throws Exception {
        // Given: inventory with sufficient stock
        String skuCode = "TEST-SKU-001";
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        String orderNumber = UUID.randomUUID().toString();

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // When: OrderPlacedEvent is sent
        OrderPlacedEvent event = OrderPlacedEvent.create(
                orderNumber,
                List.of(OrderLineItemEvent.builder()
                        .skuCode(skuCode)
                        .price(BigDecimal.valueOf(50))
                        .quantity(5)
                        .build())
        );
        producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
        producer.flush();

        // Then: inventory should be reserved
        await().atMost(15, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            Inventory updatedInventory = inventoryRepository.findBySkuCode(skuCode);
            assertThat(updatedInventory.getReservedQuantity()).isEqualTo(5);
            assertThat(updatedInventory.getAvailableQuantity()).isEqualTo(95);
        });

        // And: reservation record should be created
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber);
            assertThat(reservations).hasSize(1);
            assertThat(reservations.get(0).getSkuCode()).isEqualTo(skuCode);
            assertThat(reservations.get(0).getQuantity()).isEqualTo(5);
        });

        // And: InventoryReservedEvent should be published
        ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);

        boolean foundReservedEvent = false;
        for (var record : records) {
            if (record.value() instanceof InventoryReservedEvent reservedEvent) {
                if (reservedEvent.getOrderNumber().equals(orderNumber)) {
                    foundReservedEvent = true;
                    break;
                }
            }
        }
        assertThat(foundReservedEvent).isTrue();
    }

    @Test
    void whenOrderPlacedWithInsufficientInventory_shouldPublishFailedEvent() throws Exception {
        // Given: inventory with insufficient stock
        String skuCode = "TEST-SKU-002";
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(3);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        String orderNumber = UUID.randomUUID().toString();

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // Create a fresh consumer for this test to ensure clean state
        Map<String, Object> freshConsumerProps = KafkaTestUtils.consumerProps("failed-test-consumer-" + orderNumber, "true", embeddedKafkaBroker);
        freshConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        freshConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
        freshConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        try (Consumer<String, BaseEvent> failedConsumer = new DefaultKafkaConsumerFactory<>(
                freshConsumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer()) {
            failedConsumer.subscribe(List.of("inventory.failed"));

            // When: OrderPlacedEvent is sent requesting more than available
            OrderPlacedEvent event = OrderPlacedEvent.create(
                    orderNumber,
                    List.of(OrderLineItemEvent.builder()
                            .skuCode(skuCode)
                            .price(BigDecimal.valueOf(50))
                            .quantity(10) // Requesting 10 but only 3 available
                            .build())
            );
            producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
            producer.flush();

            // Then: InventoryReservationFailedEvent should be published
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(failedConsumer, Duration.ofSeconds(5));

                boolean foundFailedEvent = false;
                for (var record : records) {
                    if (record.value() instanceof InventoryReservationFailedEvent failedEvent) {
                        if (failedEvent.getOrderNumber().equals(orderNumber)) {
                            foundFailedEvent = true;
                            assertThat(failedEvent.getFailedSkuCodes()).contains(skuCode);
                            assertThat(failedEvent.getReason()).contains(skuCode);
                            break;
                        }
                    }
                }
                assertThat(foundFailedEvent).isTrue();
            });
        }

        // And: no reservation should be created
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber);
        assertThat(reservations).isEmpty();

        // And: inventory reserved quantity should remain unchanged
        Inventory unchangedInventory = inventoryRepository.findBySkuCode(skuCode);
        assertThat(unchangedInventory.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    void whenOrderPlacedWithNonExistentSku_shouldPublishFailedEvent() throws Exception {
        // Given: no inventory for the requested SKU
        String orderNumber = UUID.randomUUID().toString();
        String nonExistentSku = "NON-EXISTENT-SKU";

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // Create a fresh consumer for this test to ensure clean state
        Map<String, Object> freshConsumerProps = KafkaTestUtils.consumerProps("nonexistent-test-consumer-" + orderNumber, "true", embeddedKafkaBroker);
        freshConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        freshConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
        freshConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        try (Consumer<String, BaseEvent> failedConsumer = new DefaultKafkaConsumerFactory<>(
                freshConsumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer()) {
            failedConsumer.subscribe(List.of("inventory.failed"));

            // When: OrderPlacedEvent is sent for non-existent SKU
            OrderPlacedEvent event = OrderPlacedEvent.create(
                    orderNumber,
                    List.of(OrderLineItemEvent.builder()
                            .skuCode(nonExistentSku)
                            .price(BigDecimal.valueOf(50))
                            .quantity(1)
                            .build())
            );
            producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
            producer.flush();

            // Then: InventoryReservationFailedEvent should be published
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(failedConsumer, Duration.ofSeconds(5));

                boolean foundFailedEvent = false;
                for (var record : records) {
                    if (record.value() instanceof InventoryReservationFailedEvent failedEvent) {
                        if (failedEvent.getOrderNumber().equals(orderNumber)) {
                            foundFailedEvent = true;
                            assertThat(failedEvent.getFailedSkuCodes()).contains(nonExistentSku);
                            break;
                        }
                    }
                }
                assertThat(foundFailedEvent)
                        .as("Expected InventoryReservationFailedEvent for order %s", orderNumber)
                        .isTrue();
            });
        }
    }

    @Test
    void whenOrderPlacedWithMultipleItems_allShouldBeReserved() throws Exception {
        // Given: inventory for multiple SKUs
        String skuCode1 = "MULTI-SKU-001";
        String skuCode2 = "MULTI-SKU-002";

        Inventory inventory1 = new Inventory();
        inventory1.setSkuCode(skuCode1);
        inventory1.setQuantity(50);
        inventory1.setReservedQuantity(0);
        inventoryRepository.save(inventory1);

        Inventory inventory2 = new Inventory();
        inventory2.setSkuCode(skuCode2);
        inventory2.setQuantity(30);
        inventory2.setReservedQuantity(0);
        inventoryRepository.save(inventory2);

        String orderNumber = UUID.randomUUID().toString();

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // When: OrderPlacedEvent with multiple items is sent
        OrderPlacedEvent event = OrderPlacedEvent.create(
                orderNumber,
                List.of(
                        OrderLineItemEvent.builder()
                                .skuCode(skuCode1)
                                .price(BigDecimal.valueOf(100))
                                .quantity(10)
                                .build(),
                        OrderLineItemEvent.builder()
                                .skuCode(skuCode2)
                                .price(BigDecimal.valueOf(200))
                                .quantity(5)
                                .build()
                )
        );
        producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
        producer.flush();

        // Then: both inventories should be reserved
        await().atMost(15, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            Inventory updatedInventory1 = inventoryRepository.findBySkuCode(skuCode1);
            Inventory updatedInventory2 = inventoryRepository.findBySkuCode(skuCode2);

            assertThat(updatedInventory1.getReservedQuantity()).isEqualTo(10);
            assertThat(updatedInventory2.getReservedQuantity()).isEqualTo(5);
        });

        // And: reservations should be created for both items
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber);
            assertThat(reservations).hasSize(2);
        });

        // And: InventoryReservedEvent should be published
        ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        boolean foundReservedEvent = false;
        for (var record : records) {
            if (record.value() instanceof InventoryReservedEvent reservedEvent) {
                if (reservedEvent.getOrderNumber().equals(orderNumber)) {
                    foundReservedEvent = true;
                    break;
                }
            }
        }
        assertThat(foundReservedEvent).isTrue();
    }
}
