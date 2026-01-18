package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for placing a new order")
public class OrderRequest {

    @Schema(description = "List of order line items")
    private java.util.List<OrderLineItemsDto> orderLineItemsDtoList;

}
