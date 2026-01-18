package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Details of an order line item")
public class OrderLineItemsDto {

    @Schema(description = "Unique identifier of the line item", example = "1")
    private Long id;

    @Schema(description = "Stock Keeping Unit code of the product", example = "IPHONE_15_PRO")
    private String skuCode;

    @Schema(description = "Price of the product", example = "999.99")
    private Double price;

    @Schema(description = "Quantity ordered", example = "2")
    private Integer quantity;
}
