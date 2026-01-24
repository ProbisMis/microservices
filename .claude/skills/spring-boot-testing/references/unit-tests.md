# Unit Test Patterns

## Required Imports

```java
// JUnit 5
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Mockito
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// AssertJ
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

## Test Class Anatomy

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    // Shared test fixtures
    private OrderRequest orderRequest;
    private OrderLineItemsDto lineItem;

    @BeforeEach
    void setUp() {
        lineItem = new OrderLineItemsDto();
        lineItem.setSkuCode("SKU-001");
        lineItem.setPrice(99.99);
        lineItem.setQuantity(2);

        orderRequest = new OrderRequest();
        orderRequest.setOrderLineItemsDtoList(Arrays.asList(lineItem));
    }

    @Nested
    @DisplayName("placeOrder - Success Scenarios")
    class PlaceOrderSuccessTests {
        // Success test cases
    }

    @Nested
    @DisplayName("placeOrder - Failure Scenarios")
    class PlaceOrderFailureTests {
        // Failure test cases
    }

    @Nested
    @DisplayName("placeOrder - Edge Cases")
    class PlaceOrderEdgeCaseTests {
        // Edge case test cases
    }
}
```

## Stubbing Patterns

### when/thenReturn
```java
// Simple return value
when(repository.findById(1L)).thenReturn(Optional.of(entity));

// Return for any argument
when(repository.findBySkuCodeIn(anyList())).thenReturn(inventories);

// Multiple returns (first call, second call, etc.)
when(client.call()).thenReturn(result1).thenReturn(result2);

// Return null
when(client.isInStock(anyList())).thenReturn(null);

// Return empty collection
when(repository.findAll()).thenReturn(Collections.emptyList());
```

### thenAnswer (Dynamic Return)
```java
// Return the argument that was passed
when(repository.save(any(Order.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));

// Custom logic
when(repository.save(any(Entity.class)))
    .thenAnswer(invocation -> {
        Entity arg = invocation.getArgument(0);
        arg.setId(1L);
        return arg;
    });
```

### doNothing (Void Methods)
```java
doNothing().when(repository).delete(entity);
doNothing().when(repository).deleteById(anyString());
```

### thenThrow (Exceptions)
```java
when(client.call()).thenThrow(new RuntimeException("Connection failed"));

doThrow(new RuntimeException()).when(repository).delete(any());
```

## Verification Patterns

### Basic Verification
```java
// Verify called once (default)
verify(repository).save(any(Order.class));

// Verify call count
verify(repository, times(1)).save(any());
verify(repository, times(2)).findById(any());

// Verify never called
verify(repository, never()).save(any(Order.class));

// Verify at least/at most
verify(repository, atLeast(1)).findAll();
verify(repository, atMost(3)).save(any());
```

### ArgumentCaptor
```java
@Test
void shouldCaptureAndVerifySavedEntity() {
    // Given
    when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    service.placeOrder(orderRequest);

    // Then
    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(repository).save(orderCaptor.capture());

    Order savedOrder = orderCaptor.getValue();
    assertThat(savedOrder.getOrderNumber()).isNotNull();
    assertThat(savedOrder.getOrderLineItemsList()).hasSize(2);
}

// Capture list argument
ArgumentCaptor<List<String>> skuCodesCaptor = ArgumentCaptor.forClass(List.class);
verify(inventoryClient).isInStock(skuCodesCaptor.capture());
assertThat(skuCodesCaptor.getValue()).containsExactlyInAnyOrder("SKU-001", "SKU-002");
```

## Test Method Patterns

### Success Test
```java
@Test
@DisplayName("Should place order successfully when all items are in stock")
void shouldPlaceOrderSuccessfullyWhenAllItemsInStock() {
    // Given
    List<InventoryResponse> inventoryResponses = Arrays.asList(
        InventoryResponse.builder().skuCode("SKU-001").isInStock(true).build()
    );
    when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    orderService.placeOrder(orderRequest);

    // Then
    verify(orderRepository, times(1)).save(any(Order.class));
}
```

### Failure Test (Exception)
```java
@Test
@DisplayName("Should throw exception when item is out of stock")
void shouldThrowExceptionWhenItemIsOutOfStock() {
    // Given
    List<InventoryResponse> inventoryResponses = Arrays.asList(
        InventoryResponse.builder().skuCode("SKU-001").isInStock(false).build()
    );
    when(inventoryClient.isInStock(anyList())).thenReturn(inventoryResponses);

    // When & Then
    assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Product is not in stock, please try again later");

    verify(orderRepository, never()).save(any(Order.class));
}
```

### Edge Case Test
```java
@Test
@DisplayName("Should handle order with zero quantity item")
void shouldHandleOrderWithZeroQuantityItem() {
    // Given
    OrderLineItemsDto zeroQuantityItem = new OrderLineItemsDto();
    zeroQuantityItem.setSkuCode("SKU-ZERO");
    zeroQuantityItem.setQuantity(0);
    orderRequest.setOrderLineItemsDtoList(Collections.singletonList(zeroQuantityItem));

    when(inventoryClient.isInStock(anyList())).thenReturn(
        Collections.singletonList(InventoryResponse.builder().skuCode("SKU-ZERO").isInStock(true).build())
    );
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    orderService.placeOrder(orderRequest);

    // Then
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(captor.capture());
    assertThat(captor.getValue().getOrderLineItemsList().get(0).getQuantity()).isEqualTo(0);
}
```

## Helper Methods and Test Data Builders

```java
// Helper method for creating test entities
private Order createOrder(Long id, String orderNumber, String skuCode, Double price, Integer quantity) {
    Order order = new Order();
    order.setId(id);
    order.setOrderNumber(orderNumber);

    OrderLineItems lineItem = new OrderLineItems();
    lineItem.setSkuCode(skuCode);
    lineItem.setPrice(price);
    lineItem.setQuantity(quantity);

    order.getOrderLineItemsList().add(lineItem);
    return order;
}

// Using Lombok @Builder for test data
Product product = Product.builder()
    .id("507f1f77bcf86cd799439011")
    .name("Test Product")
    .description("Test Description")
    .price(BigDecimal.valueOf(99.99))
    .build();
```

## Common ArgumentMatchers

```java
any()                    // Any value (including null)
any(Order.class)         // Any Order instance
anyList()                // Any List
anyString()              // Any String
anyLong()                // Any long/Long
eq("specific-value")     // Exact value match
argThat(o -> o.getId() > 0)  // Custom matcher
```
