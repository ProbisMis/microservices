package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for placing a new order")
public class OrderRequest {

    @NotEmpty(message = "Order must contain at least one line item")
    @Valid
    @Schema(description = "List of order line items")
    private List<OrderLineItemsDto> orderLineItemsDtoList;

}
