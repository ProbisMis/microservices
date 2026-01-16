package com.demo.programming.inventory_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.demo.programming.inventory_service.model.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Inventory findBySkuCode(String skuCode);
    List<Inventory> findBySkuCodeIn(List<String> skuCode);

}
