package com.demo.programming.inventory_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for creating or updating inventory")
public class InventoryRequest {

    @Schema(description = "Stock Keeping Unit code of the product", example = "IPHONE_15_PRO")
    private String skuCode;

    @Schema(description = "Quantity in stock", example = "100")
    private Integer quantity;
}
