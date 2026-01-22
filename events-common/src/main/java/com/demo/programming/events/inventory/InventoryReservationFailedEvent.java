package com.demo.programming.events.inventory;

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
public class InventoryReservationFailedEvent extends BaseEvent {
    private String orderNumber;
    private String reason;
    private List<String> failedSkuCodes;

    public static InventoryReservationFailedEvent create(String orderNumber, String reason, List<String> failedSkuCodes) {
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.builder()
                .orderNumber(orderNumber)
                .reason(reason)
                .failedSkuCodes(failedSkuCodes)
                .build();
        event.initializeBase("INVENTORY_RESERVATION_FAILED");
        event.setCorrelationId(orderNumber);
        return event;
    }
}
