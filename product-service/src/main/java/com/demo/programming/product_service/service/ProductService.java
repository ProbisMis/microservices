package com.demo.programming.product_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.exceptions.ResourceNotFoundException;
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

        @Transactional
        public ProductResponse createProduct(ProductRequest productRequest) {
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

                return mapToProductResponse(savedProduct);
        }

        @Transactional(readOnly = true)
        public List<ProductResponse> getAllProducts() {
                return productRepository.findAll().stream()
                                .map(this::mapToProductResponse)
                                .toList();
        }

        @Transactional(readOnly = true)
        public ProductResponse getProductById(String id) {
                return productRepository.findById(id)
                                .map(this::mapToProductResponse)
                                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        }

        @Transactional
        public ProductResponse updateProduct(String id, ProductRequest productRequest) {
                Product product = productRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

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

        @Transactional
        public void deleteProduct(String id) {
                if (!productRepository.existsById(id)) {
                        throw new ResourceNotFoundException("Product", "id", id);
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