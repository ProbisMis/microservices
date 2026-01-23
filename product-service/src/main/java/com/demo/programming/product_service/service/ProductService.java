package com.demo.programming.product_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.demo.programming.product_service.dto.ProductRequest;
import com.demo.programming.product_service.dto.ProductResponse;
import com.demo.programming.product_service.kafka.producer.ProductEventProducer;
import com.demo.programming.product_service.model.Product;
import com.demo.programming.product_service.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductEventProducer productEventProducer;

    public void createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product {} is saved", savedProduct.getId());

        productEventProducer.publishProductCreated(
                savedProduct.getId(),
                savedProduct.getName(),
                savedProduct.getDescription(),
                savedProduct.getPrice());
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToProductResponse)
                .toList();
    }

    public ProductResponse getProductById(String id) {
        return productRepository.findById(id)
                .map(this::mapToProductResponse)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    public ProductResponse updateProduct(String id, ProductRequest productRequest) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());

        Product updatedProduct = productRepository.save(product);
        log.info("Product {} is updated", updatedProduct.getId());

        productEventProducer.publishProductUpdated(
                updatedProduct.getId(),
                updatedProduct.getName(),
                updatedProduct.getDescription(),
                updatedProduct.getPrice());

        return mapToProductResponse(updatedProduct);
    }

    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found with id: " + id);
        }
        productEventProducer.publishProductDeleted(id);
        productRepository.deleteById(id);
        log.info("Product {} is deleted", id);
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