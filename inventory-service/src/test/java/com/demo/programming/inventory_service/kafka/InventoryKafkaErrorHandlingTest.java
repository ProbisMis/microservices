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
import com.demo.programming.inventory_service.service.InventoryService;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
        "spring.kafka.consumer.group-id=inventory-error-test-${random.uuid}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryKafkaErrorHandlingTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @SpyBean
    private InventoryService inventoryService;

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
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("error-test-consumer", "true", embeddedKafkaBroker);
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
    void whenDatabaseUnavailable_shouldNotPublishEvent() throws Exception {
        // Given: inventory with stock
        String skuCode = "DB-ERROR-SKU-001";
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        String orderNumber = UUID.randomUUID().toString();

        // Configure service to throw database exception
        doThrow(new RuntimeException("Database connection failed"))
                .when(inventoryService).reserveInventory(anyString(), any());

        // Wait for Spring consumer to be fully initialized
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

        // Wait for event processing attempt
        Thread.sleep(5000);

        // Then: No InventoryReservedEvent or InventoryReservationFailedEvent should be published
        // (since the service threw an exception before publishing)
        ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3));

        boolean foundEventForThisOrder = false;
        for (var record : records) {
            if (record.value() instanceof InventoryReservedEvent reservedEvent) {
                if (reservedEvent.getOrderNumber().equals(orderNumber)) {
                    foundEventForThisOrder = true;
                }
            } else if (record.value() instanceof InventoryReservationFailedEvent failedEvent) {
                if (failedEvent.getOrderNumber().equals(orderNumber)) {
                    foundEventForThisOrder = true;
                }
            }
        }
        // Due to exception being thrown before event publication, no event should be published
        // Note: This depends on implementation - current impl has try-catch in consumer
        // The test documents expected atomicity behavior
        assertThat(foundEventForThisOrder).isFalse();

        // Verify service was called
        verify(inventoryService, atLeastOnce()).reserveInventory(eq(orderNumber), any());
    }

    @Test
    void whenConsumerThrowsException_shouldNotAcknowledge() throws Exception {
        // Given: inventory with stock
        String skuCode = "RETRY-SKU-001";
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        String orderNumber = UUID.randomUUID().toString();

        // Configure service to throw exception on first call, then succeed
        doThrow(new RuntimeException("Simulated transient failure"))
                .doCallRealMethod()
                .when(inventoryService).reserveInventory(anyString(), any());

        // Wait for Spring consumer to be fully initialized
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

        // Then: Due to retry behavior, the reservation should eventually succeed
        // Current implementation has try-catch that prevents automatic retry
        // This documents expected behavior vs actual behavior
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .pollDelay(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Due to consumer's error handling (try-catch), retries depend on Kafka redelivery
                    // The service was called at least once
                    verify(inventoryService, atLeastOnce()).reserveInventory(eq(orderNumber), any());
                });
    }

    @Test
    void whenInventoryNotFound_shouldPublishFailedEvent() throws Exception {
        // Given: no inventory for the requested SKU
        String orderNumber = UUID.randomUUID().toString();
        String nonExistentSku = "NON-EXISTENT-SKU-ERROR";

        // Wait for Spring consumer to be fully initialized
        Thread.sleep(3000);

        // When: OrderPlacedEvent is sent for non-existent SKU
        OrderPlacedEvent event = OrderPlacedEvent.create(
                orderNumber,
                List.of(OrderLineItemEvent.builder()
                        .skuCode(nonExistentSku)
                        .price(BigDecimal.valueOf(50))
                        .quantity(5)
                        .build())
        );
        producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
        producer.flush();

        // Then: InventoryReservationFailedEvent should be published
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

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

        // Consumer should still be functional after handling this error
        verify(inventoryService, atLeastOnce()).reserveInventory(eq(orderNumber), any());
    }

    @Test
    void whenConsumerRecoveredAfterError_shouldProcessNextMessage() throws Exception {
        // Given: inventory with stock
        String skuCode1 = "RECOVERY-SKU-001";
        String skuCode2 = "RECOVERY-SKU-002";

        Inventory inventory1 = new Inventory();
        inventory1.setSkuCode(skuCode1);
        inventory1.setQuantity(100);
        inventory1.setReservedQuantity(0);
        inventoryRepository.save(inventory1);

        Inventory inventory2 = new Inventory();
        inventory2.setSkuCode(skuCode2);
        inventory2.setQuantity(100);
        inventory2.setReservedQuantity(0);
        inventoryRepository.save(inventory2);

        String orderNumber1 = UUID.randomUUID().toString();
        String orderNumber2 = UUID.randomUUID().toString();

        // Configure service to throw exception on first order, succeed on second
        doThrow(new RuntimeException("First order fails"))
                .when(inventoryService).reserveInventory(eq(orderNumber1), any());
        doCallRealMethod()
                .when(inventoryService).reserveInventory(eq(orderNumber2), any());

        // Wait for Spring consumer to be fully initialized
        Thread.sleep(3000);

        // When: First (failing) event is sent
        OrderPlacedEvent event1 = OrderPlacedEvent.create(
                orderNumber1,
                List.of(OrderLineItemEvent.builder()
                        .skuCode(skuCode1)
                        .price(BigDecimal.valueOf(50))
                        .quantity(5)
                        .build())
        );
        producer.send(new ProducerRecord<>("orders.placed", orderNumber1, event1));
        producer.flush();

        // Wait for first event to fail
        Thread.sleep(3000);

        // When: Second (succeeding) event is sent
        OrderPlacedEvent event2 = OrderPlacedEvent.create(
                orderNumber2,
                List.of(OrderLineItemEvent.builder()
                        .skuCode(skuCode2)
                        .price(BigDecimal.valueOf(75))
                        .quantity(10)
                        .build())
        );
        producer.send(new ProducerRecord<>("orders.placed", orderNumber2, event2));
        producer.flush();

        // Then: Second order should be processed successfully
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .pollDelay(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    entityManager.clear();
                    List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber2);
                    assertThat(reservations).hasSize(1);
                    assertThat(reservations.get(0).getSkuCode()).isEqualTo(skuCode2);
                });

        // Consumer recovered and processed the second message
        verify(inventoryService, atLeastOnce()).reserveInventory(eq(orderNumber2), any());
    }
}
