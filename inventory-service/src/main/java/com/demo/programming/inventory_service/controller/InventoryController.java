package com.demo.programming.inventory_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.demo.programming.inventory_service.dto.InventoryRequest;
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
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Check inventory stock", description = "Checks if products with the given SKU codes are in stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved inventory status")
    })
    public List<InventoryResponse> isSkuExists(
            @Parameter(description = "List of SKU codes to check", required = true)
            @RequestParam("sku-code") List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    @GetMapping("/all")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get all inventory", description = "Retrieves all inventory items")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all inventory")
    })
    public List<InventoryResponse> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/{skuCode}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get inventory by SKU code", description = "Retrieves inventory for a specific SKU code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved inventory"),
            @ApiResponse(responseCode = "404", description = "Inventory not found")
    })
    public InventoryResponse getInventoryBySkuCode(
            @Parameter(description = "SKU code", required = true)
            @PathVariable String skuCode) {
        return inventoryService.getInventoryBySkuCode(skuCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add new inventory", description = "Creates a new inventory item")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Inventory created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or inventory already exists")
    })
    public InventoryResponse addInventory(@RequestBody InventoryRequest inventoryRequest) {
        return inventoryService.addInventory(inventoryRequest);
    }

    @PutMapping("/{skuCode}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update inventory stock", description = "Updates the quantity for an existing inventory item")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventory updated successfully"),
            @ApiResponse(responseCode = "404", description = "Inventory not found")
    })
    public InventoryResponse updateStock(
            @Parameter(description = "SKU code", required = true)
            @PathVariable String skuCode,
            @Parameter(description = "New quantity", required = true)
            @RequestParam Integer quantity) {
        return inventoryService.updateStock(skuCode, quantity);
    }

    @DeleteMapping("/{skuCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete inventory", description = "Deletes an inventory item")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Inventory deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Inventory not found")
    })
    public void deleteInventory(
            @Parameter(description = "SKU code", required = true)
            @PathVariable String skuCode) {
        inventoryService.deleteInventory(skuCode);
    }
}
