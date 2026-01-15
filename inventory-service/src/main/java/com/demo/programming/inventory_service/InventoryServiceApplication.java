package com.demo.programming.inventory_service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.demo.programming.inventory_service.model.Inventory;
import com.demo.programming.inventory_service.repository.InventoryRepository;

@SpringBootApplication
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

//	@Bean
//	public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
//		return args -> {
//			Inventory item1 = new Inventory();
//			item1.setSkuCode("SKU123");
//			item1.setQuantity(100);
//			inventoryRepository.save(item1);
//
//			Inventory item2 = new Inventory();
//			item2.setSkuCode("SKU456");
//			item2.setQuantity(0);
//			inventoryRepository.save(item2);
//		};
//	}
}
