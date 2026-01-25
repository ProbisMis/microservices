package com.demo.programming.product_service.kafka.producer;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.product.ProductCreatedEvent;
import com.demo.programming.events.product.ProductDeletedEvent;
import com.demo.programming.events.product.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventProducer {

    private static final String TOPIC_PRODUCTS_EVENTS = "products.events";

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;

    public void publishProductCreated(String productId, String name, String description, BigDecimal price) {
        ProductCreatedEvent event = ProductCreatedEvent.create(productId, name, description, price);
        log.info("Publishing ProductCreatedEvent for product: {}", productId);

        kafkaTemplate.send(TOPIC_PRODUCTS_EVENTS, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductCreatedEvent for product {}: {}", productId, ex.getMessage());
                    } else {
                        log.info("Successfully published ProductCreatedEvent for product {} to partition {}",
                                productId, result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishProductUpdated(String productId, String name, String description, BigDecimal price) {
        ProductUpdatedEvent event = ProductUpdatedEvent.create(productId, name, description, price);
        log.info("Publishing ProductUpdatedEvent for product: {}", productId);

        kafkaTemplate.send(TOPIC_PRODUCTS_EVENTS, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductUpdatedEvent for product {}: {}", productId, ex.getMessage());
                    } else {
                        log.info("Successfully published ProductUpdatedEvent for product {} to partition {}",
                                productId, result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishProductDeleted(String productId) {
        ProductDeletedEvent event = ProductDeletedEvent.create(productId);
        log.info("Publishing ProductDeletedEvent for product: {}", productId);

        kafkaTemplate.send(TOPIC_PRODUCTS_EVENTS, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductDeletedEvent for product {}: {}", productId, ex.getMessage());
                    } else {
                        log.info("Successfully published ProductDeletedEvent for product {} to partition {}",
                                productId, result.getRecordMetadata().partition());
                    }
                });
    }
}
