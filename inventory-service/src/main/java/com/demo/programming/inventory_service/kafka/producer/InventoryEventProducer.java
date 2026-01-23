package com.demo.programming.inventory_service.kafka.producer;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.inventory.InventoryReservationFailedEvent;
import com.demo.programming.events.inventory.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private static final String TOPIC_INVENTORY_RESERVED = "inventory.reserved";
    private static final String TOPIC_INVENTORY_FAILED = "inventory.failed";

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;

    public void publishInventoryReserved(String orderNumber) {
        InventoryReservedEvent event = InventoryReservedEvent.create(orderNumber);
        log.info("Publishing InventoryReservedEvent for order: {}", orderNumber);

        kafkaTemplate.send(TOPIC_INVENTORY_RESERVED, orderNumber, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryReservedEvent for order {}: {}", orderNumber, ex.getMessage());
                    } else {
                        log.info("Successfully published InventoryReservedEvent for order {} to partition {}",
                                orderNumber, result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishInventoryFailed(String orderNumber, String reason, List<String> failedSkuCodes) {
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.create(orderNumber, reason, failedSkuCodes);
        log.info("Publishing InventoryReservationFailedEvent for order: {}, reason: {}", orderNumber, reason);

        kafkaTemplate.send(TOPIC_INVENTORY_FAILED, orderNumber, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryReservationFailedEvent for order {}: {}", orderNumber, ex.getMessage());
                    } else {
                        log.info("Successfully published InventoryReservationFailedEvent for order {} to partition {}",
                                orderNumber, result.getRecordMetadata().partition());
                    }
                });
    }
}
