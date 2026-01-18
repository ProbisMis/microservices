package com.demo.programming.product_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for creating a new product")
public class ProductRequest {

    @Schema(description = "Name of the product", example = "iPhone 15 Pro")
    private String name;

    @Schema(description = "Detailed description of the product", example = "Latest Apple smartphone with A17 Pro chip")
    private String description;

    @Schema(description = "Price of the product", example = "999.99")
    private BigDecimal price;
}
