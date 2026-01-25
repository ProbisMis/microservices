package com.demo.programming.inventory_service.kafka.consumer;

import com.demo.programming.events.order.OrderPlacedEvent;
import com.demo.programming.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "orders.placed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent for order: {}", event.getOrderNumber());

        try {
            inventoryService.reserveInventory(event.getOrderNumber(), event.getOrderLineItems());
            log.info("Successfully processed OrderPlacedEvent for order: {}", event.getOrderNumber());
        } catch (Exception e) {
            log.error("Error processing OrderPlacedEvent for order {}: {}", event.getOrderNumber(), e.getMessage());
        }
    }
}
