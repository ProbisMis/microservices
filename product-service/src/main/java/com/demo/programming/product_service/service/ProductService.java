package com.demo.programming.product_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.demo.programming.product_service.dto.ProductRequest;
import com.demo.programming.product_service.dto.ProductResponse;
import com.demo.programming.product_service.model.Product;
import com.demo.programming.product_service.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    
    private final ProductRepository productRepository;

    public void createProduct(ProductRequest productRequest) {
        // Implementation for creating a product
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();

        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
        
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll().stream()
            .collect(Collectors.toList());
        return products.stream().map(this::mapToProductResponse).toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}