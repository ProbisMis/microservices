package com.demo.programming.exceptions;

import com.demo.programming.exceptions.base.BaseException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class InsufficientStockException extends BaseException {

    private final List<String> skuCodes;

    public InsufficientStockException(List<String> skuCodes) {
        super(
                String.format("Insufficient stock for SKU codes: %s", skuCodes),
                "INSUFFICIENT_STOCK",
                HttpStatus.CONFLICT,
                "Inventory"
        );
        this.skuCodes = skuCodes;
    }

    public InsufficientStockException(String skuCode) {
        this(List.of(skuCode));
    }

    public List<String> getSkuCodes() {
        return skuCodes;
    }
}
