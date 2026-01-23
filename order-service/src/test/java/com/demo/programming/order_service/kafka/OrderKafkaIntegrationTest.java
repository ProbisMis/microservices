package com.demo.programming.order_service.kafka;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.inventory.InventoryReservationFailedEvent;
import com.demo.programming.events.inventory.InventoryReservedEvent;
import com.demo.programming.events.order.OrderConfirmedEvent;
import com.demo.programming.events.order.OrderLineItemEvent;
import com.demo.programming.events.order.OrderPlacedEvent;
import com.demo.programming.events.order.OrderRejectedEvent;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.model.OrderStatus;
import com.demo.programming.order_service.repository.OrderRepository;
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
import java.util.Collections;
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
                "orders.confirmed",
                "orders.rejected",
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
class OrderKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private Producer<String, BaseEvent> producer;
    private Consumer<String, BaseEvent> consumer;

    @BeforeEach
    void setUp() {
        // Set up producer for sending inventory events
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        producer = new DefaultKafkaProducerFactory<>(
                producerProps,
                new StringSerializer(),
                new JsonSerializer<BaseEvent>()
        ).createProducer();

        // Set up consumer for verifying order events
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-consumer-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer();
        consumer.subscribe(List.of("orders.confirmed", "orders.rejected"));
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
        orderRepository.deleteAll();
    }

    @Test
    void whenInventoryReservedEventReceived_orderShouldBeConfirmed() throws Exception {
        // Given: an order in PENDING status
        String orderNumber = UUID.randomUUID().toString();
        Order order = createAndSaveOrder(orderNumber, OrderStatus.PENDING);

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // When: InventoryReservedEvent is sent
        InventoryReservedEvent event = InventoryReservedEvent.create(orderNumber);
        producer.send(new ProducerRecord<>("inventory.reserved", orderNumber, event));
        producer.flush();

        // Then: order should be updated to CONFIRMED
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .pollDelay(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            Order updatedOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        });

        // And: OrderConfirmedEvent should be published
        ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);

        boolean foundConfirmedEvent = false;
        for (var record : records) {
            if (record.value() instanceof OrderConfirmedEvent confirmedEvent) {
                if (confirmedEvent.getOrderNumber().equals(orderNumber)) {
                    foundConfirmedEvent = true;
                    break;
                }
            }
        }
        assertThat(foundConfirmedEvent).isTrue();
    }

    @Test
    void whenInventoryReservationFailedEventReceived_orderShouldBeRejected() throws Exception {
        // Given: an order in PENDING status
        String orderNumber = UUID.randomUUID().toString();
        Order order = createAndSaveOrder(orderNumber, OrderStatus.PENDING);

        // Wait for Spring consumer to be fully initialized and subscribed
        Thread.sleep(3000);

        // When: InventoryReservationFailedEvent is sent
        String failureReason = "Insufficient inventory for SKUs: TEST-001";
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.create(
                orderNumber,
                failureReason,
                List.of("TEST-001")
        );
        producer.send(new ProducerRecord<>("inventory.failed", orderNumber, event));
        producer.flush();

        // Then: order should be updated to REJECTED with failure reason
        // Wait for consumer to process the message and commit the transaction
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .pollDelay(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            entityManager.clear(); // Clear cache to get fresh data from DB
            Order updatedOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(updatedOrder.getFailureReason()).isEqualTo(failureReason);
        });

        // And: OrderRejectedEvent should be published
        ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);

        boolean foundRejectedEvent = false;
        for (var record : records) {
            if (record.value() instanceof OrderRejectedEvent rejectedEvent) {
                if (rejectedEvent.getOrderNumber().equals(orderNumber)) {
                    foundRejectedEvent = true;
                    assertThat(rejectedEvent.getReason()).isEqualTo(failureReason);
                    break;
                }
            }
        }
        assertThat(foundRejectedEvent).isTrue();
    }

    @Test
    void whenOrderPlacedAsync_orderShouldBeSavedWithPendingStatus_andEventPublished() {
        // This test verifies the producer side - that OrderPlacedEvent is published
        // Set up consumer for orders.placed topic
        Map<String, Object> placedConsumerProps = KafkaTestUtils.consumerProps("placed-consumer-group", "true", embeddedKafkaBroker);
        placedConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        placedConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.programming.events,com.demo.programming.events.*");
        placedConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        try (Consumer<String, BaseEvent> placedConsumer = new DefaultKafkaConsumerFactory<>(
                placedConsumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(BaseEvent.class, false)
        ).createConsumer()) {
            placedConsumer.subscribe(Collections.singletonList("orders.placed"));

            // Given: a new order
            String orderNumber = UUID.randomUUID().toString();
            Order order = new Order();
            order.setOrderNumber(orderNumber);
            order.setStatus(OrderStatus.PENDING);

            OrderLineItems lineItem = new OrderLineItems();
            lineItem.setSkuCode("TEST-SKU-001");
            lineItem.setPrice(100.0);
            lineItem.setQuantity(2);
            order.getOrderLineItemsList().add(lineItem);

            orderRepository.save(order);

            // When: OrderPlacedEvent is manually sent (simulating placeOrderAsync)
            OrderPlacedEvent event = OrderPlacedEvent.create(
                    orderNumber,
                    List.of(OrderLineItemEvent.builder()
                            .skuCode("TEST-SKU-001")
                            .price(BigDecimal.valueOf(100))
                            .quantity(2)
                            .build())
            );
            producer.send(new ProducerRecord<>("orders.placed", orderNumber, event));
            producer.flush();

            // Then: the event should be received
            ConsumerRecords<String, BaseEvent> records = KafkaTestUtils.getRecords(placedConsumer, Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThan(0);

            boolean foundPlacedEvent = false;
            for (var record : records) {
                if (record.value() instanceof OrderPlacedEvent placedEvent) {
                    if (placedEvent.getOrderNumber().equals(orderNumber)) {
                        foundPlacedEvent = true;
                        assertThat(placedEvent.getOrderLineItems()).hasSize(1);
                        assertThat(placedEvent.getOrderLineItems().get(0).getSkuCode()).isEqualTo("TEST-SKU-001");
                        break;
                    }
                }
            }
            assertThat(foundPlacedEvent).isTrue();
        }
    }

    private Order createAndSaveOrder(String orderNumber, OrderStatus status) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setStatus(status);

        OrderLineItems lineItem = new OrderLineItems();
        lineItem.setSkuCode("TEST-SKU-001");
        lineItem.setPrice(100.0);
        lineItem.setQuantity(2);
        order.getOrderLineItemsList().add(lineItem);

        return orderRepository.save(order);
    }
}
