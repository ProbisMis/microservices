package com.demo.programming.inventory_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.programming.inventory_service.dto.InventoryRequest;
import com.demo.programming.inventory_service.dto.InventoryResponse;
import com.demo.programming.inventory_service.model.Inventory;
import com.demo.programming.inventory_service.repository.InventoryRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inStockInventory;
    private Inventory outOfStockInventory;
    private Inventory zeroStockInventory;

    @BeforeEach
    void setUp() {
        inStockInventory = new Inventory();
        inStockInventory.setId(1L);
        inStockInventory.setSkuCode("SKU-IN-STOCK");
        inStockInventory.setQuantity(100);

        outOfStockInventory = new Inventory();
        outOfStockInventory.setId(2L);
        outOfStockInventory.setSkuCode("SKU-OUT-STOCK");
        outOfStockInventory.setQuantity(0);

        zeroStockInventory = new Inventory();
        zeroStockInventory.setId(3L);
        zeroStockInventory.setSkuCode("SKU-ZERO");
        zeroStockInventory.setQuantity(0);
    }

    @Nested
    @DisplayName("isInStock - Single Item Tests")
    class SingleItemTests {

        @Test
        @DisplayName("Should return in stock for item with positive quantity")
        void shouldReturnInStockForItemWithPositiveQuantity() {
            // Given
            List<String> skuCodes = Collections.singletonList("SKU-IN-STOCK");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(inStockInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSkuCode()).isEqualTo("SKU-IN-STOCK");
            assertThat(result.get(0).isInStock()).isTrue();
            verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
        }

        @Test
        @DisplayName("Should return out of stock for item with zero quantity")
        void shouldReturnOutOfStockForItemWithZeroQuantity() {
            // Given
            List<String> skuCodes = Collections.singletonList("SKU-OUT-STOCK");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(outOfStockInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSkuCode()).isEqualTo("SKU-OUT-STOCK");
            assertThat(result.get(0).isInStock()).isFalse();
        }

        @Test
        @DisplayName("Should return in stock for item with quantity of 1")
        void shouldReturnInStockForItemWithQuantityOfOne() {
            // Given
            Inventory oneItemInventory = new Inventory();
            oneItemInventory.setId(4L);
            oneItemInventory.setSkuCode("SKU-ONE");
            oneItemInventory.setQuantity(1);

            List<String> skuCodes = Collections.singletonList("SKU-ONE");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(oneItemInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isInStock()).isTrue();
        }
    }

    @Nested
    @DisplayName("isInStock - Multiple Items Tests")
    class MultipleItemsTests {

        @Test
        @DisplayName("Should return correct stock status for multiple items")
        void shouldReturnCorrectStockStatusForMultipleItems() {
            // Given
            List<String> skuCodes = Arrays.asList("SKU-IN-STOCK", "SKU-OUT-STOCK");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Arrays.asList(inStockInventory, outOfStockInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(2);

            InventoryResponse inStockResponse = result.stream()
                    .filter(r -> r.getSkuCode().equals("SKU-IN-STOCK"))
                    .findFirst().orElseThrow();
            assertThat(inStockResponse.isInStock()).isTrue();

            InventoryResponse outOfStockResponse = result.stream()
                    .filter(r -> r.getSkuCode().equals("SKU-OUT-STOCK"))
                    .findFirst().orElseThrow();
            assertThat(outOfStockResponse.isInStock()).isFalse();
        }

        @Test
        @DisplayName("Should return all in stock when all items have positive quantity")
        void shouldReturnAllInStockWhenAllItemsHavePositiveQuantity() {
            // Given
            Inventory inventory1 = new Inventory(1L, "SKU-1", 50, 0);
            Inventory inventory2 = new Inventory(2L, "SKU-2", 100, 0);
            Inventory inventory3 = new Inventory(3L, "SKU-3", 25, 0);

            List<String> skuCodes = Arrays.asList("SKU-1", "SKU-2", "SKU-3");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Arrays.asList(inventory1, inventory2, inventory3));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(InventoryResponse::isInStock);
        }

        @Test
        @DisplayName("Should return all out of stock when all items have zero quantity")
        void shouldReturnAllOutOfStockWhenAllItemsHaveZeroQuantity() {
            // Given
            Inventory inventory1 = new Inventory(1L, "SKU-EMPTY-1", 0, 0);
            Inventory inventory2 = new Inventory(2L, "SKU-EMPTY-2", 0, 0);

            List<String> skuCodes = Arrays.asList("SKU-EMPTY-1", "SKU-EMPTY-2");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Arrays.asList(inventory1, inventory2));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).noneMatch(InventoryResponse::isInStock);
        }

        @Test
        @DisplayName("Should handle large batch of SKU codes")
        void shouldHandleLargeBatchOfSkuCodes() {
            // Given
            List<Inventory> inventories = Arrays.asList(
                    new Inventory(1L, "SKU-1", 10, 0),
                    new Inventory(2L, "SKU-2", 20, 0),
                    new Inventory(3L, "SKU-3", 0, 0),
                    new Inventory(4L, "SKU-4", 30, 0),
                    new Inventory(5L, "SKU-5", 0, 0)
            );
            List<String> skuCodes = Arrays.asList("SKU-1", "SKU-2", "SKU-3", "SKU-4", "SKU-5");
            when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(inventories);

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(5);
            long inStockCount = result.stream().filter(InventoryResponse::isInStock).count();
            long outOfStockCount = result.stream().filter(r -> !r.isInStock()).count();
            assertThat(inStockCount).isEqualTo(3);
            assertThat(outOfStockCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("isInStock - Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty list when no matching SKU codes found")
        void shouldReturnEmptyListWhenNoMatchingSkuCodesFound() {
            // Given
            List<String> skuCodes = Arrays.asList("NON-EXISTENT-1", "NON-EXISTENT-2");
            when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(Collections.emptyList());

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for empty SKU codes list")
        void shouldReturnEmptyListForEmptySkuCodesList() {
            // Given
            List<String> skuCodes = Collections.emptyList();
            when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(Collections.emptyList());

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle inventory with very high quantity")
        void shouldHandleInventoryWithVeryHighQuantity() {
            // Given
            Inventory highQuantityInventory = new Inventory();
            highQuantityInventory.setId(1L);
            highQuantityInventory.setSkuCode("SKU-HIGH");
            highQuantityInventory.setQuantity(Integer.MAX_VALUE);

            List<String> skuCodes = Collections.singletonList("SKU-HIGH");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(highQuantityInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isInStock()).isTrue();
        }

        @Test
        @DisplayName("Should correctly map SKU code in response")
        void shouldCorrectlyMapSkuCodeInResponse() {
            // Given
            String expectedSkuCode = "SKU-SPECIAL-123";
            Inventory inventory = new Inventory(1L, expectedSkuCode, 10, 0);

            List<String> skuCodes = Collections.singletonList(expectedSkuCode);
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(inventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSkuCode()).isEqualTo(expectedSkuCode);
        }

        @Test
        @DisplayName("Should handle partial match of SKU codes")
        void shouldHandlePartialMatchOfSkuCodes() {
            // Given - requesting 3 SKUs but only 2 exist in inventory
            List<String> skuCodes = Arrays.asList("SKU-EXISTS-1", "SKU-EXISTS-2", "SKU-NOT-EXISTS");
            List<Inventory> foundInventories = Arrays.asList(
                    new Inventory(1L, "SKU-EXISTS-1", 10, 0),
                    new Inventory(2L, "SKU-EXISTS-2", 20, 0)
            );
            when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(foundInventories);

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then - only returns 2 items since only 2 exist
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r ->
                    r.getSkuCode().equals("SKU-EXISTS-1") || r.getSkuCode().equals("SKU-EXISTS-2")
            );
        }
    }

    @Nested
    @DisplayName("isInStock - Boundary Tests")
    class BoundaryTests {

        @Test
        @DisplayName("Should return false for quantity exactly zero")
        void shouldReturnFalseForQuantityExactlyZero() {
            // Given
            Inventory zeroInventory = new Inventory(1L, "SKU-BOUNDARY-ZERO", 0, 0);
            List<String> skuCodes = Collections.singletonList("SKU-BOUNDARY-ZERO");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(zeroInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result.get(0).isInStock()).isFalse();
        }

        @Test
        @DisplayName("Should return true for quantity exactly one")
        void shouldReturnTrueForQuantityExactlyOne() {
            // Given
            Inventory oneInventory = new Inventory(1L, "SKU-BOUNDARY-ONE", 1, 0);
            List<String> skuCodes = Collections.singletonList("SKU-BOUNDARY-ONE");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(oneInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result.get(0).isInStock()).isTrue();
        }

        @Test
        @DisplayName("Should handle negative quantity as out of stock")
        void shouldHandleNegativeQuantityAsOutOfStock() {
            // Given - edge case if negative quantities are possible
            Inventory negativeInventory = new Inventory(1L, "SKU-NEGATIVE", -5, 0);
            List<String> skuCodes = Collections.singletonList("SKU-NEGATIVE");
            when(inventoryRepository.findBySkuCodeIn(skuCodes))
                    .thenReturn(Collections.singletonList(negativeInventory));

            // When
            List<InventoryResponse> result = inventoryService.isInStock(skuCodes);

            // Then
            assertThat(result.get(0).isInStock()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAllInventory Tests")
    class GetAllInventoryTests {

        @Test
        @DisplayName("Should return all inventory items")
        void shouldReturnAllInventoryItems() {
            // Given
            List<Inventory> inventories = Arrays.asList(
                    new Inventory(1L, "SKU-001", 100, 0),
                    new Inventory(2L, "SKU-002", 50, 0),
                    new Inventory(3L, "SKU-003", 0, 0)
            );
            when(inventoryRepository.findAll()).thenReturn(inventories);

            // When
            List<InventoryResponse> result = inventoryService.getAllInventory();

            // Then
            assertThat(result).hasSize(3);
            verify(inventoryRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no inventory exists")
        void shouldReturnEmptyListWhenNoInventoryExists() {
            // Given
            when(inventoryRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<InventoryResponse> result = inventoryService.getAllInventory();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getInventoryBySkuCode Tests")
    class GetInventoryBySkuCodeTests {

        @Test
        @DisplayName("Should return inventory when found by SKU code")
        void shouldReturnInventoryWhenFoundBySkuCode() {
            // Given
            when(inventoryRepository.findBySkuCode("SKU-IN-STOCK")).thenReturn(inStockInventory);

            // When
            InventoryResponse result = inventoryService.getInventoryBySkuCode("SKU-IN-STOCK");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSkuCode()).isEqualTo("SKU-IN-STOCK");
            assertThat(result.isInStock()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when inventory not found")
        void shouldThrowExceptionWhenInventoryNotFound() {
            // Given
            when(inventoryRepository.findBySkuCode("NON-EXISTENT")).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> inventoryService.getInventoryBySkuCode("NON-EXISTENT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Inventory not found with skuCode: NON-EXISTENT");
        }
    }

    @Nested
    @DisplayName("addInventory Tests")
    class AddInventoryTests {

        @Test
        @DisplayName("Should add new inventory successfully")
        void shouldAddNewInventorySuccessfully() {
            // Given
            InventoryRequest request = InventoryRequest.builder()
                    .skuCode("NEW-SKU")
                    .quantity(100)
                    .build();

            Inventory savedInventory = new Inventory(1L, "NEW-SKU", 100, 0);

            when(inventoryRepository.findBySkuCode("NEW-SKU")).thenReturn(null);
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(savedInventory);

            // When
            InventoryResponse result = inventoryService.addInventory(request);

            // Then
            assertThat(result.getSkuCode()).isEqualTo("NEW-SKU");
            assertThat(result.isInStock()).isTrue();

            ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryRepository).save(inventoryCaptor.capture());
            assertThat(inventoryCaptor.getValue().getQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should throw exception when inventory already exists")
        void shouldThrowExceptionWhenInventoryAlreadyExists() {
            // Given
            InventoryRequest request = InventoryRequest.builder()
                    .skuCode("SKU-IN-STOCK")
                    .quantity(50)
                    .build();

            when(inventoryRepository.findBySkuCode("SKU-IN-STOCK")).thenReturn(inStockInventory);

            // When & Then
            assertThatThrownBy(() -> inventoryService.addInventory(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Inventory already exists with skuCode: SKU-IN-STOCK");

            verify(inventoryRepository, never()).save(any(Inventory.class));
        }

        @Test
        @DisplayName("Should add inventory with zero quantity")
        void shouldAddInventoryWithZeroQuantity() {
            // Given
            InventoryRequest request = InventoryRequest.builder()
                    .skuCode("EMPTY-SKU")
                    .quantity(0)
                    .build();

            Inventory savedInventory = new Inventory(1L, "EMPTY-SKU", 0, 0);

            when(inventoryRepository.findBySkuCode("EMPTY-SKU")).thenReturn(null);
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(savedInventory);

            // When
            InventoryResponse result = inventoryService.addInventory(request);

            // Then
            assertThat(result.isInStock()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateStock Tests")
    class UpdateStockTests {

        @Test
        @DisplayName("Should update stock successfully")
        void shouldUpdateStockSuccessfully() {
            // Given
            Inventory updatedInventory = new Inventory(1L, "SKU-IN-STOCK", 200, 0);

            when(inventoryRepository.findBySkuCode("SKU-IN-STOCK")).thenReturn(inStockInventory);
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

            // When
            InventoryResponse result = inventoryService.updateStock("SKU-IN-STOCK", 200);

            // Then
            assertThat(result.getSkuCode()).isEqualTo("SKU-IN-STOCK");
            assertThat(result.isInStock()).isTrue();
            verify(inventoryRepository, times(1)).save(any(Inventory.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent inventory")
        void shouldThrowExceptionWhenUpdatingNonExistentInventory() {
            // Given
            when(inventoryRepository.findBySkuCode("NON-EXISTENT")).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> inventoryService.updateStock("NON-EXISTENT", 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Inventory not found with skuCode: NON-EXISTENT");

            verify(inventoryRepository, never()).save(any(Inventory.class));
        }

        @Test
        @DisplayName("Should update stock to zero")
        void shouldUpdateStockToZero() {
            // Given
            Inventory updatedInventory = new Inventory(1L, "SKU-IN-STOCK", 0, 0);

            when(inventoryRepository.findBySkuCode("SKU-IN-STOCK")).thenReturn(inStockInventory);
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

            // When
            InventoryResponse result = inventoryService.updateStock("SKU-IN-STOCK", 0);

            // Then
            assertThat(result.isInStock()).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteInventory Tests")
    class DeleteInventoryTests {

        @Test
        @DisplayName("Should delete inventory successfully")
        void shouldDeleteInventorySuccessfully() {
            // Given
            when(inventoryRepository.findBySkuCode("SKU-IN-STOCK")).thenReturn(inStockInventory);
            doNothing().when(inventoryRepository).delete(inStockInventory);

            // When
            inventoryService.deleteInventory("SKU-IN-STOCK");

            // Then
            verify(inventoryRepository, times(1)).delete(inStockInventory);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent inventory")
        void shouldThrowExceptionWhenDeletingNonExistentInventory() {
            // Given
            when(inventoryRepository.findBySkuCode("NON-EXISTENT")).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> inventoryService.deleteInventory("NON-EXISTENT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Inventory not found with skuCode: NON-EXISTENT");

            verify(inventoryRepository, never()).delete(any(Inventory.class));
        }
    }
}
