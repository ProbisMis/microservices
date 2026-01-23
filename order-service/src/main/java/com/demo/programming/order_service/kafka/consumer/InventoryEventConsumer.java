package com.demo.programming.order_service.kafka.consumer;

import com.demo.programming.events.inventory.InventoryReservationFailedEvent;
import com.demo.programming.events.inventory.InventoryReservedEvent;
import com.demo.programming.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "inventory.reserved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Received inventory reserved event for order: {}", event.getOrderNumber());
        try {
            orderService.confirmOrder(event.getOrderNumber());
            log.info("Order {} confirmed successfully", event.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to confirm order {}: {}", event.getOrderNumber(), e.getMessage());
        }
    }

    @KafkaListener(topics = "inventory.failed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.info("Received inventory reservation failed event for order: {}", event.getOrderNumber());
        try {
            orderService.rejectOrder(event.getOrderNumber(), event.getReason());
            log.info("Order {} rejected due to: {}", event.getOrderNumber(), event.getReason());
        } catch (Exception e) {
            log.error("Failed to reject order {}: {}", event.getOrderNumber(), e.getMessage());
        }
    }
}
