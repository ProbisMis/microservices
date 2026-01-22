package com.demo.programming.events.inventory;

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
public class InventoryReservedEvent extends BaseEvent {
    private String orderNumber;

    public static InventoryReservedEvent create(String orderNumber) {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderNumber(orderNumber)
                .build();
        event.initializeBase("INVENTORY_RESERVED");
        event.setCorrelationId(orderNumber);
        return event;
    }
}
