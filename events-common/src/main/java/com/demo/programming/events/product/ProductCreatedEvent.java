package com.demo.programming.events.product;

import com.demo.programming.events.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductCreatedEvent extends BaseEvent {
    private String productId;
    private String name;
    private String description;
    private BigDecimal price;

    public static ProductCreatedEvent create(String productId, String name, String description, BigDecimal price) {
        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .productId(productId)
                .name(name)
                .description(description)
                .price(price)
                .build();
        event.initializeBase("PRODUCT_CREATED");
        event.setCorrelationId(productId);
        return event;
    }
}
