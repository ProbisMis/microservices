package com.demo.programming.order_service.kafka.producer;

import com.demo.programming.events.BaseEvent;
import com.demo.programming.events.order.OrderConfirmedEvent;
import com.demo.programming.events.order.OrderLineItemEvent;
import com.demo.programming.events.order.OrderPlacedEvent;
import com.demo.programming.events.order.OrderRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String ORDERS_PLACED_TOPIC = "orders.placed";
    private static final String ORDERS_CONFIRMED_TOPIC = "orders.confirmed";
    private static final String ORDERS_REJECTED_TOPIC = "orders.rejected";

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;

    public void publishOrderPlaced(String orderNumber, List<OrderLineItemEvent> orderLineItems) {
        OrderPlacedEvent event = OrderPlacedEvent.create(orderNumber, orderLineItems);
        sendMessage(ORDERS_PLACED_TOPIC, orderNumber, event);
    }

    public void publishOrderConfirmed(String orderNumber) {
        OrderConfirmedEvent event = OrderConfirmedEvent.create(orderNumber);
        sendMessage(ORDERS_CONFIRMED_TOPIC, orderNumber, event);
    }

    public void publishOrderRejected(String orderNumber, String reason) {
        OrderRejectedEvent event = OrderRejectedEvent.create(orderNumber, reason);
        sendMessage(ORDERS_REJECTED_TOPIC, orderNumber, event);
    }

    private void sendMessage(String topic, String key, BaseEvent event) {
        CompletableFuture<SendResult<String, BaseEvent>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send message to topic {}: {}", topic, ex.getMessage());
            } else {
                log.info("Message sent to topic {} with key {} at offset {}",
                        topic, key, result.getRecordMetadata().offset());
            }
        });
    }
}
