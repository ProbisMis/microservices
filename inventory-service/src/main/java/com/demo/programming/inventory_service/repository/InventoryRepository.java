package com.demo.programming.inventory_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<com.demo.programming.inventory_service.model.Inventory, Long> {
    com.demo.programming.inventory_service.model.Inventory findBySkuCode(String skuCode);

}
