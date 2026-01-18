package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response body containing order details")
public class OrderResponse {

    @Schema(description = "Unique identifier of the order", example = "1")
    private Long id;

    @Schema(description = "Order number (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String orderNumber;

    @Schema(description = "List of order line items")
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
