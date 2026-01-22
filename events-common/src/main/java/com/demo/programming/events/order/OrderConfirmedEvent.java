package com.demo.programming.events.order;

import com.demo.programming.events.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderConfirmedEvent extends BaseEvent {
    private String orderNumber;

    public static OrderConfirmedEvent create(String orderNumber) {
        OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                .orderNumber(orderNumber)
                .build();
        event.initializeBase("ORDER_CONFIRMED");
        event.setCorrelationId(orderNumber);
        return event;
    }
}
