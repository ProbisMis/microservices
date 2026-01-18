package com.demo.programming.inventory_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response body containing inventory stock status")
public class InventoryResponse {

    @Schema(description = "Stock Keeping Unit code of the product", example = "IPHONE_15_PRO")
    private String skuCode;

    @Schema(description = "Indicates if the product is in stock", example = "true")
    private boolean isInStock;
}
