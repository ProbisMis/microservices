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
public class OrderRejectedEvent extends BaseEvent {
    private String orderNumber;
    private String reason;

    public static OrderRejectedEvent create(String orderNumber, String reason) {
        OrderRejectedEvent event = OrderRejectedEvent.builder()
                .orderNumber(orderNumber)
                .reason(reason)
                .build();
        event.initializeBase("ORDER_REJECTED");
        event.setCorrelationId(orderNumber);
        return event;
    }
}
