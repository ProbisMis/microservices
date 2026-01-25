package com.demo.programming.order_service.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.demo.programming.order_service.dto.InventoryResponse;

@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    @GetMapping("/api/inventory")
    List<InventoryResponse> isInStock(@RequestParam("sku-code") List<String> skuCode);
}
