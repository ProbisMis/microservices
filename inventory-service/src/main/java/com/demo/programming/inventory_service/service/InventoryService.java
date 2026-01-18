package com.demo.programming.inventory_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.inventory_service.dto.InventoryRequest;
import com.demo.programming.inventory_service.dto.InventoryResponse;
import com.demo.programming.inventory_service.model.Inventory;
import com.demo.programming.inventory_service.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCodes) {
        return inventoryRepository.findBySkuCodeIn(skuCodes).stream()
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySkuCode(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory not found with skuCode: " + skuCode);
        }
        return InventoryResponse.builder()
                .skuCode(inventory.getSkuCode())
                .isInStock(inventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public InventoryResponse addInventory(InventoryRequest inventoryRequest) {
        Inventory existingInventory = inventoryRepository.findBySkuCode(inventoryRequest.getSkuCode());
        if (existingInventory != null) {
            throw new IllegalArgumentException("Inventory already exists with skuCode: " + inventoryRequest.getSkuCode());
        }

        Inventory inventory = new Inventory();
        inventory.setSkuCode(inventoryRequest.getSkuCode());
        inventory.setQuantity(inventoryRequest.getQuantity());

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created for skuCode: {}", savedInventory.getSkuCode());

        return InventoryResponse.builder()
                .skuCode(savedInventory.getSkuCode())
                .isInStock(savedInventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public InventoryResponse updateStock(String skuCode, Integer quantity) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory not found with skuCode: " + skuCode);
        }

        inventory.setQuantity(quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated for skuCode: {}, new quantity: {}", skuCode, quantity);

        return InventoryResponse.builder()
                .skuCode(updatedInventory.getSkuCode())
                .isInStock(updatedInventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public void deleteInventory(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory not found with skuCode: " + skuCode);
        }
        inventoryRepository.delete(inventory);
        log.info("Inventory deleted for skuCode: {}", skuCode);
    }
}
