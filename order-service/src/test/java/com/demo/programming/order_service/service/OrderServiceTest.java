package com.demo.programming.order_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.demo.programming.exceptions.InsufficientStockException;
import com.demo.programming.exceptions.ResourceNotFoundException;
import com.demo.programming.order_service.client.InventoryClient;
import com.demo.programming.order_service.dto.InventoryResponse;
import com.demo.programming.order_service.dto.OrderLineItemsDto;
import com.demo.programming.order_service.dto.OrderRequest;
import com.demo.programming.order_service.dto.OrderResponse;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest orderRequest;
    private OrderLineItemsDto lineItem1;
    private OrderLineItemsDto lineItem2;

    @BeforeEach
    void setUp() {
        lineItem1 = new OrderLineItemsDto();
        lineItem1.setId(1L);
        lineItem1.setSkuCode("SKU-001");
        lineItem1.setPrice(new BigDecimal("99.99"));
        lineItem1.setQuantity(2);

        lineItem2 = new OrderLineItemsDto();
        lineItem2.setId(2L);
        lineItem2.setSkuCode("SKU-002");
        lineItem2.setPrice(new BigDecimal("49.99"));
        lineItem2.setQuantity(1);

        orderRequest = new OrderRequest();
        orderRequest.setOrderLineItemsDtoList(Arrays.asList(lineItem1, lineItem2));
    }

    @Nested
    @DisplayName("placeOrder - Success Scenarios")
    class PlaceOrderSuccessTests {

        @Test
        @DisplayName("Should place order successfully when all items are in stock")
        void shouldPlaceOrderSuccessfullyWhenAllItemsInStock() {
            // Given
            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(1)).save(orderCaptor.capture());

            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getOrderNumber()).isNotNull();
            assertThat(savedOrder.getOrderLineItemsList()).hasSize(2);
        }

        @Test
        @DisplayName("Should generate unique UUID for order number")
        void shouldGenerateUniqueUuidForOrderNumber() {
            // Given
            List<InventoryResponse> inventoryResponses = Collections.singletonList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build()
            );
            orderRequest.setOrderLineItemsDtoList(Collections.singletonList(lineItem1));
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());

            String orderNumber = orderCaptor.getValue().getOrderNumber();
            assertThat(orderNumber).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should correctly map line item DTOs to entities")
        void shouldCorrectlyMapLineItemDtosToEntities() {
            // Given
            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());

            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getOrderLineItemsList().get(0).getSkuCode()).isEqualTo("SKU-001");
            assertThat(savedOrder.getOrderLineItemsList().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(savedOrder.getOrderLineItemsList().get(0).getQuantity()).isEqualTo(2);
            assertThat(savedOrder.getOrderLineItemsList().get(1).getSkuCode()).isEqualTo("SKU-002");
            assertThat(savedOrder.getOrderLineItemsList().get(1).getPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
            assertThat(savedOrder.getOrderLineItemsList().get(1).getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should place single item order successfully")
        void shouldPlaceSingleItemOrderSuccessfully() {
            // Given
            orderRequest.setOrderLineItemsDtoList(Collections.singletonList(lineItem1));
            List<InventoryResponse> inventoryResponses = Collections.singletonList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderLineItemsList()).hasSize(1);
        }

        @Test
        @DisplayName("Should call inventory client with correct SKU codes")
        void shouldCallInventoryClientWithCorrectSkuCodes() {
            // Given
            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<List<String>> skuCodesCaptor = ArgumentCaptor.forClass(List.class);
            verify(inventoryClient).isInStock(skuCodesCaptor.capture());

            List<String> capturedSkuCodes = skuCodesCaptor.getValue();
            assertThat(capturedSkuCodes).containsExactlyInAnyOrder("SKU-001", "SKU-002");
        }
    }

    @Nested
    @DisplayName("placeOrder - Failure Scenarios")
    class PlaceOrderFailureTests {

        @Test
        @DisplayName("Should throw exception when item is out of stock")
        void shouldThrowExceptionWhenItemIsOutOfStock() {
            // Given
            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(false).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock for SKU codes");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Should throw exception when all items are out of stock")
        void shouldThrowExceptionWhenAllItemsOutOfStock() {
            // Given
            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(false).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(false).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock for SKU codes");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Should throw exception when inventory response is null")
        void shouldThrowExceptionWhenInventoryResponseIsNull() {
            // Given
            when(inventoryClient.isInStock(anyList())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock for SKU codes");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Should throw exception when inventory response is empty")
        void shouldThrowExceptionWhenInventoryResponseIsEmpty() {
            // Given
            when(inventoryClient.isInStock(anyList())).thenReturn(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock for SKU codes");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Should not save order when single item out of stock in multi-item order")
        void shouldNotSaveOrderWhenSingleItemOutOfStockInMultiItemOrder() {
            // Given
            OrderLineItemsDto lineItem3 = new OrderLineItemsDto();
            lineItem3.setSkuCode("SKU-003");
            lineItem3.setPrice(new BigDecimal("29.99"));
            lineItem3.setQuantity(5);
            orderRequest.setOrderLineItemsDtoList(Arrays.asList(lineItem1, lineItem2, lineItem3));

            List<InventoryResponse> inventoryResponses = Arrays.asList(
                    InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-002").isInStock(true).build(),
                    InventoryResponse.builder().skuCode("SKU-003").isInStock(false).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);

            // When & Then
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
                    .isInstanceOf(InsufficientStockException.class);

            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("placeOrder - Edge Cases")
    class PlaceOrderEdgeCaseTests {

        @Test
        @DisplayName("Should handle order with zero quantity item")
        void shouldHandleOrderWithZeroQuantityItem() {
            // Given
            OrderLineItemsDto zeroQuantityItem = new OrderLineItemsDto();
            zeroQuantityItem.setSkuCode("SKU-ZERO");
            zeroQuantityItem.setPrice(new BigDecimal("10.00"));
            zeroQuantityItem.setQuantity(0);
            orderRequest.setOrderLineItemsDtoList(Collections.singletonList(zeroQuantityItem));

            List<InventoryResponse> inventoryResponses = Collections.singletonList(
                    InventoryResponse.builder().skuCode("SKU-ZERO").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderLineItemsList().get(0).getQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle order with high quantity item")
        void shouldHandleOrderWithHighQuantityItem() {
            // Given
            OrderLineItemsDto highQuantityItem = new OrderLineItemsDto();
            highQuantityItem.setSkuCode("SKU-BULK");
            highQuantityItem.setPrice(new BigDecimal("5.00"));
            highQuantityItem.setQuantity(10000);
            orderRequest.setOrderLineItemsDtoList(Collections.singletonList(highQuantityItem));

            List<InventoryResponse> inventoryResponses = Collections.singletonList(
                    InventoryResponse.builder().skuCode("SKU-BULK").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @Test
        @DisplayName("Should handle order with zero price item")
        void shouldHandleOrderWithZeroPriceItem() {
            // Given
            OrderLineItemsDto freeItem = new OrderLineItemsDto();
            freeItem.setSkuCode("SKU-FREE");
            freeItem.setPrice(BigDecimal.ZERO);
            freeItem.setQuantity(1);
            orderRequest.setOrderLineItemsDtoList(Collections.singletonList(freeItem));

            List<InventoryResponse> inventoryResponses = Collections.singletonList(
                    InventoryResponse.builder().skuCode("SKU-FREE").isInStock(true).build()
            );
            when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            orderService.placeOrder(orderRequest);

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderLineItemsList().get(0).getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getAllOrders Tests")
    class GetAllOrdersTests {

        @Test
        @DisplayName("Should return all orders")
        void shouldReturnAllOrders() {
            // Given
            Order order1 = createOrder(1L, "order-123", "SKU-001", new BigDecimal("99.99"), 2);
            Order order2 = createOrder(2L, "order-456", "SKU-002", new BigDecimal("49.99"), 1);

            when(orderRepository.findAll()).thenReturn(Arrays.asList(order1, order2));

            // When
            List<OrderResponse> result = orderService.getAllOrders();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderNumber()).isEqualTo("order-123");
            assertThat(result.get(1).getOrderNumber()).isEqualTo("order-456");
            verify(orderRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no orders exist")
        void shouldReturnEmptyListWhenNoOrdersExist() {
            // Given
            when(orderRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<OrderResponse> result = orderService.getAllOrders();

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should correctly map order with multiple line items")
        void shouldCorrectlyMapOrderWithMultipleLineItems() {
            // Given
            Order order = new Order();
            order.setId(1L);
            order.setOrderNumber("order-multi");

            OrderLineItems item1 = new OrderLineItems(1L, "SKU-001", new BigDecimal("99.99"), 2);
            OrderLineItems item2 = new OrderLineItems(2L, "SKU-002", new BigDecimal("49.99"), 1);
            order.getOrderLineItemsList().add(item1);
            order.getOrderLineItemsList().add(item2);

            when(orderRepository.findAll()).thenReturn(Collections.singletonList(order));

            // When
            List<OrderResponse> result = orderService.getAllOrders();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOrderLineItemsDtoList()).hasSize(2);
            assertThat(result.get(0).getOrderLineItemsDtoList().get(0).getSkuCode()).isEqualTo("SKU-001");
            assertThat(result.get(0).getOrderLineItemsDtoList().get(1).getSkuCode()).isEqualTo("SKU-002");
        }
    }

    @Nested
    @DisplayName("getOrderByOrderNumber Tests")
    class GetOrderByOrderNumberTests {

        @Test
        @DisplayName("Should return order when found by order number")
        void shouldReturnOrderWhenFoundByOrderNumber() {
            // Given
            Order order = createOrder(1L, "order-123", "SKU-001", new BigDecimal("99.99"), 2);
            when(orderRepository.findByOrderNumber("order-123")).thenReturn(Optional.of(order));

            // When
            OrderResponse result = orderService.getOrderByOrderNumber("order-123");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderNumber()).isEqualTo("order-123");
            assertThat(result.getId()).isEqualTo(1L);
            verify(orderRepository, times(1)).findByOrderNumber("order-123");
        }

        @Test
        @DisplayName("Should throw exception when order not found")
        void shouldThrowExceptionWhenOrderNotFound() {
            // Given
            when(orderRepository.findByOrderNumber("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> orderService.getOrderByOrderNumber("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order")
                    .hasMessageContaining("non-existent");
        }

        @Test
        @DisplayName("Should correctly map all order fields to response")
        void shouldCorrectlyMapAllOrderFieldsToResponse() {
            // Given
            Order order = new Order();
            order.setId(1L);
            order.setOrderNumber("test-order-uuid");

            OrderLineItems item = new OrderLineItems(1L, "SKU-TEST", new BigDecimal("150.00"), 3);
            order.getOrderLineItemsList().add(item);

            when(orderRepository.findByOrderNumber("test-order-uuid")).thenReturn(Optional.of(order));

            // When
            OrderResponse result = orderService.getOrderByOrderNumber("test-order-uuid");

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getOrderNumber()).isEqualTo("test-order-uuid");
            assertThat(result.getOrderLineItemsDtoList()).hasSize(1);
            assertThat(result.getOrderLineItemsDtoList().get(0).getSkuCode()).isEqualTo("SKU-TEST");
            assertThat(result.getOrderLineItemsDtoList().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(result.getOrderLineItemsDtoList().get(0).getQuantity()).isEqualTo(3);
        }
    }

    private Order createOrder(Long id, String orderNumber, String skuCode, BigDecimal price, Integer quantity) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(orderNumber);

        OrderLineItems lineItem = new OrderLineItems();
        lineItem.setId(1L);
        lineItem.setSkuCode(skuCode);
        lineItem.setPrice(price);
        lineItem.setQuantity(quantity);

        order.getOrderLineItemsList().add(lineItem);
        return order;
    }
}
