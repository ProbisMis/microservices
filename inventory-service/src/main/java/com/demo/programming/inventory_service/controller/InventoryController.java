package com.demo.programming.inventory_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.demo.programming.inventory_service.dto.InventoryResponse;
import com.demo.programming.inventory_service.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(org.springframework.http.HttpStatus.OK)
    @Operation(summary = "Check inventory stock", description = "Checks if products with the given SKU codes are in stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved inventory status")
    })
    public List<InventoryResponse> isSkuExists(
            @Parameter(description = "List of SKU codes to check", required = true)
            @RequestParam("sku-code") List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }
}
