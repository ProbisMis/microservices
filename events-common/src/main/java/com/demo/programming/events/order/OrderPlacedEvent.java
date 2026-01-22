package com.demo.programming.events.order;

import com.demo.programming.events.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderPlacedEvent extends BaseEvent {
    private String orderNumber;
    private List<OrderLineItemEvent> orderLineItems;

    public static OrderPlacedEvent create(String orderNumber, List<OrderLineItemEvent> orderLineItems) {
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderNumber(orderNumber)
                .orderLineItems(orderLineItems)
                .build();
        event.initializeBase("ORDER_PLACED");
        event.setCorrelationId(orderNumber);
        return event;
    }
}
