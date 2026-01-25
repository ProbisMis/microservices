package com.demo.programming.order_service.client;

import com.demo.programming.order_service.dto.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public List<InventoryResponse> isInStock(List<String> skuCode) {
        log.warn("Fallback triggered for inventory check. SKU codes: {}", skuCode);
        return Collections.emptyList();
    }
}
