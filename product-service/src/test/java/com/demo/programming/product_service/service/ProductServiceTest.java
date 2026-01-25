package com.demo.programming.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.programming.product_service.dto.ProductRequest;
import com.demo.programming.product_service.dto.ProductResponse;
import com.demo.programming.product_service.kafka.producer.ProductEventProducer;
import com.demo.programming.product_service.model.Product;
import com.demo.programming.product_service.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEventProducer productEventProducer;

    @InjectMocks
    private ProductService productService;

    private ProductRequest productRequest;
    private Product product;

    @BeforeEach
    void setUp() {
        productRequest = ProductRequest.builder()
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .build();

        product = Product.builder()
                .id("507f1f77bcf86cd799439011")
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(99.99))
                .build();
    }

    @Nested
    @DisplayName("createProduct Tests")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product successfully")
        void shouldCreateProductSuccessfully() {
            // Given
            when(productRepository.save(any(Product.class))).thenReturn(product);

            // When
            productService.createProduct(productRequest);

            // Then
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository, times(1)).save(productCaptor.capture());

            Product savedProduct = productCaptor.getValue();
            assertThat(savedProduct.getName()).isEqualTo(productRequest.getName());
            assertThat(savedProduct.getDescription()).isEqualTo(productRequest.getDescription());
            assertThat(savedProduct.getPrice()).isEqualTo(productRequest.getPrice());
        }

        @Test
        @DisplayName("Should create product with zero price")
        void shouldCreateProductWithZeroPrice() {
            // Given
            ProductRequest zeroPrice = ProductRequest.builder()
                    .name("Free Product")
                    .description("A free product")
                    .price(BigDecimal.ZERO)
                    .build();
            when(productRepository.save(any(Product.class))).thenReturn(product);

            // When
            productService.createProduct(zeroPrice);

            // Then
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getPrice()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should create product with high precision price")
        void shouldCreateProductWithHighPrecisionPrice() {
            // Given
            BigDecimal highPrecisionPrice = new BigDecimal("1234.5678");
            ProductRequest precisionRequest = ProductRequest.builder()
                    .name("Precision Product")
                    .description("Product with high precision price")
                    .price(highPrecisionPrice)
                    .build();
            when(productRepository.save(any(Product.class))).thenReturn(product);

            // When
            productService.createProduct(precisionRequest);

            // Then
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getPrice()).isEqualTo(highPrecisionPrice);
        }

        @Test
        @DisplayName("Should create product with empty description")
        void shouldCreateProductWithEmptyDescription() {
            // Given
            ProductRequest emptyDescRequest = ProductRequest.builder()
                    .name("Product Name")
                    .description("")
                    .price(BigDecimal.valueOf(50.00))
                    .build();
            when(productRepository.save(any(Product.class))).thenReturn(product);

            // When
            productService.createProduct(emptyDescRequest);

            // Then
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getDescription()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllProducts Tests")
    class GetAllProductsTests {

        @Test
        @DisplayName("Should return all products")
        void shouldReturnAllProducts() {
            // Given
            Product product1 = Product.builder()
                    .id("1")
                    .name("Product 1")
                    .description("Description 1")
                    .price(BigDecimal.valueOf(100.00))
                    .build();

            Product product2 = Product.builder()
                    .id("2")
                    .name("Product 2")
                    .description("Description 2")
                    .price(BigDecimal.valueOf(200.00))
                    .build();

            when(productRepository.findAll()).thenReturn(Arrays.asList(product1, product2));

            // When
            List<ProductResponse> result = productService.getAllProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("1");
            assertThat(result.get(0).getName()).isEqualTo("Product 1");
            assertThat(result.get(1).getId()).isEqualTo("2");
            assertThat(result.get(1).getName()).isEqualTo("Product 2");
            verify(productRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no products exist")
        void shouldReturnEmptyListWhenNoProductsExist() {
            // Given
            when(productRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<ProductResponse> result = productService.getAllProducts();

            // Then
            assertThat(result).isEmpty();
            verify(productRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should correctly map all product fields to response")
        void shouldCorrectlyMapAllProductFieldsToResponse() {
            // Given
            Product product = Product.builder()
                    .id("test-id-123")
                    .name("Mapped Product")
                    .description("Mapped Description")
                    .price(BigDecimal.valueOf(999.99))
                    .build();

            when(productRepository.findAll()).thenReturn(Collections.singletonList(product));

            // When
            List<ProductResponse> result = productService.getAllProducts();

            // Then
            assertThat(result).hasSize(1);
            ProductResponse response = result.get(0);
            assertThat(response.getId()).isEqualTo("test-id-123");
            assertThat(response.getName()).isEqualTo("Mapped Product");
            assertThat(response.getDescription()).isEqualTo("Mapped Description");
            assertThat(response.getPrice()).isEqualTo(BigDecimal.valueOf(999.99));
        }

        @Test
        @DisplayName("Should handle product with null fields")
        void shouldHandleProductWithNullFields() {
            // Given
            Product productWithNulls = Product.builder()
                    .id("null-test")
                    .name(null)
                    .description(null)
                    .price(null)
                    .build();

            when(productRepository.findAll()).thenReturn(Collections.singletonList(productWithNulls));

            // When
            List<ProductResponse> result = productService.getAllProducts();

            // Then
            assertThat(result).hasSize(1);
            ProductResponse response = result.get(0);
            assertThat(response.getId()).isEqualTo("null-test");
            assertThat(response.getName()).isNull();
            assertThat(response.getDescription()).isNull();
            assertThat(response.getPrice()).isNull();
        }
    }

    @Nested
    @DisplayName("getProductById Tests")
    class GetProductByIdTests {

        @Test
        @DisplayName("Should return product when found by id")
        void shouldReturnProductWhenFoundById() {
            // Given
            when(productRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(product));

            // When
            ProductResponse result = productService.getProductById("507f1f77bcf86cd799439011");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("507f1f77bcf86cd799439011");
            assertThat(result.getName()).isEqualTo("Test Product");
            verify(productRepository, times(1)).findById("507f1f77bcf86cd799439011");
        }

        @Test
        @DisplayName("Should throw exception when product not found")
        void shouldThrowExceptionWhenProductNotFound() {
            // Given
            when(productRepository.findById("non-existent-id")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductById("non-existent-id"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found with id: non-existent-id");
        }
    }

    @Nested
    @DisplayName("updateProduct Tests")
    class UpdateProductTests {

        @Test
        @DisplayName("Should update product successfully")
        void shouldUpdateProductSuccessfully() {
            // Given
            ProductRequest updateRequest = ProductRequest.builder()
                    .name("Updated Product")
                    .description("Updated Description")
                    .price(BigDecimal.valueOf(199.99))
                    .build();

            Product updatedProduct = Product.builder()
                    .id("507f1f77bcf86cd799439011")
                    .name("Updated Product")
                    .description("Updated Description")
                    .price(BigDecimal.valueOf(199.99))
                    .build();

            when(productRepository.findById("507f1f77bcf86cd799439011")).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

            // When
            ProductResponse result = productService.updateProduct("507f1f77bcf86cd799439011", updateRequest);

            // Then
            assertThat(result.getName()).isEqualTo("Updated Product");
            assertThat(result.getDescription()).isEqualTo("Updated Description");
            assertThat(result.getPrice()).isEqualTo(BigDecimal.valueOf(199.99));
            verify(productRepository, times(1)).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent product")
        void shouldThrowExceptionWhenUpdatingNonExistentProduct() {
            // Given
            when(productRepository.findById("non-existent-id")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.updateProduct("non-existent-id", productRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found with id: non-existent-id");

            verify(productRepository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("deleteProduct Tests")
    class DeleteProductTests {

        @Test
        @DisplayName("Should delete product successfully")
        void shouldDeleteProductSuccessfully() {
            // Given
            when(productRepository.existsById("507f1f77bcf86cd799439011")).thenReturn(true);
            doNothing().when(productRepository).deleteById("507f1f77bcf86cd799439011");

            // When
            productService.deleteProduct("507f1f77bcf86cd799439011");

            // Then
            verify(productRepository, times(1)).deleteById("507f1f77bcf86cd799439011");
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent product")
        void shouldThrowExceptionWhenDeletingNonExistentProduct() {
            // Given
            when(productRepository.existsById("non-existent-id")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> productService.deleteProduct("non-existent-id"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found with id: non-existent-id");

            verify(productRepository, never()).deleteById(anyString());
        }
    }
}
