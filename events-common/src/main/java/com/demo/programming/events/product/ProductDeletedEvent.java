package com.demo.programming.events.product;

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
public class ProductDeletedEvent extends BaseEvent {
    private String productId;

    public static ProductDeletedEvent create(String productId) {
        ProductDeletedEvent event = ProductDeletedEvent.builder()
                .productId(productId)
                .build();
        event.initializeBase("PRODUCT_DELETED");
        event.setCorrelationId(productId);
        return event;
    }
}
