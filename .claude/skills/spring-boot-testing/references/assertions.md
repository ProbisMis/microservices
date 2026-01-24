# AssertJ Assertion Patterns

## Import

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

## Basic Assertions

```java
// Null checks
assertThat(result).isNotNull();
assertThat(result).isNull();

// Equality
assertThat(actual).isEqualTo(expected);
assertThat(actual).isNotEqualTo(unexpected);

// Boolean
assertThat(condition).isTrue();
assertThat(condition).isFalse();

// Same instance
assertThat(actual).isSameAs(expected);
assertThat(actual).isNotSameAs(other);
```

## String Assertions

```java
assertThat(string).isEmpty();
assertThat(string).isNotEmpty();
assertThat(string).isBlank();
assertThat(string).isNotBlank();

assertThat(string).contains("substring");
assertThat(string).containsIgnoringCase("SUBSTRING");
assertThat(string).doesNotContain("forbidden");

assertThat(string).startsWith("prefix");
assertThat(string).endsWith("suffix");

// Regex matching
assertThat(orderNumber).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
```

## Number Assertions

```java
assertThat(number).isPositive();
assertThat(number).isNegative();
assertThat(number).isZero();
assertThat(number).isNotZero();

assertThat(number).isGreaterThan(5);
assertThat(number).isGreaterThanOrEqualTo(5);
assertThat(number).isLessThan(10);
assertThat(number).isLessThanOrEqualTo(10);
assertThat(number).isBetween(1, 10);

// Floating point with tolerance
assertThat(price).isCloseTo(99.99, within(0.01));
```

## Collection Assertions

### Size and Emptiness
```java
assertThat(list).isEmpty();
assertThat(list).isNotEmpty();
assertThat(list).hasSize(3);
assertThat(list).hasSizeGreaterThan(2);
assertThat(list).hasSizeLessThanOrEqualTo(10);
```

### Content Checks
```java
// Contains elements
assertThat(list).contains(element1, element2);
assertThat(list).containsExactly(element1, element2, element3);  // Order matters
assertThat(list).containsExactlyInAnyOrder(element1, element2, element3);
assertThat(list).containsOnly(element1, element2);  // No other elements
assertThat(list).doesNotContain(forbidden);

// First/last elements
assertThat(list).startsWith(first, second);
assertThat(list).endsWith(secondToLast, last);
```

### Extracting Fields
```java
// Extract single field from objects
assertThat(products)
    .extracting(Product::getName)
    .containsExactly("Product A", "Product B");

// Extract multiple fields
assertThat(orders)
    .extracting(Order::getOrderNumber, Order::getStatus)
    .containsExactly(
        tuple("order-1", OrderStatus.CONFIRMED),
        tuple("order-2", OrderStatus.PENDING)
    );

// Extract from nested objects
assertThat(order.getOrderLineItemsList())
    .extracting(OrderLineItems::getSkuCode)
    .containsExactlyInAnyOrder("SKU-001", "SKU-002");
```

### Filtering and Matching
```java
// Filter then assert
assertThat(responses)
    .filteredOn(InventoryResponse::isInStock)
    .hasSize(3);

// All elements match condition
assertThat(responses).allMatch(InventoryResponse::isInStock);
assertThat(responses).noneMatch(r -> r.getQuantity() < 0);
assertThat(responses).anyMatch(r -> r.getSkuCode().startsWith("PREMIUM-"));

// Count matching
long inStockCount = result.stream().filter(InventoryResponse::isInStock).count();
assertThat(inStockCount).isEqualTo(3);
```

### Finding Elements
```java
// Find single element
InventoryResponse inStockResponse = result.stream()
    .filter(r -> r.getSkuCode().equals("SKU-IN-STOCK"))
    .findFirst().orElseThrow();
assertThat(inStockResponse.isInStock()).isTrue();
```

## Exception Assertions

### assertThatThrownBy
```java
assertThatThrownBy(() -> orderService.placeOrder(orderRequest))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Product is not in stock, please try again later");

// Check message contains
assertThatThrownBy(() -> service.process())
    .isInstanceOf(RuntimeException.class)
    .hasMessageContaining("failed")
    .hasMessageContaining("SKU-001");

// Check cause
assertThatThrownBy(() -> service.process())
    .isInstanceOf(ServiceException.class)
    .hasCauseInstanceOf(IOException.class);

// Check no exception thrown (implicit - just call the method)
// If it throws, test will fail
service.methodThatShouldNotThrow();
```

### assertThatCode (for no exception)
```java
assertThatCode(() -> service.safeMethod())
    .doesNotThrowAnyException();
```

## Custom Error Messages

```java
// Add context to assertions
assertThat(foundFailedEvent)
    .as("Expected InventoryReservationFailedEvent for order %s", orderNumber)
    .isTrue();

assertThat(records.count())
    .as("Should have received at least one Kafka message")
    .isGreaterThan(0);

// With description
assertThat(order.getStatus())
    .describedAs("Order status after processing")
    .isEqualTo(OrderStatus.CONFIRMED);
```

## Soft Assertions

When you want to check multiple things and see all failures:

```java
import org.assertj.core.api.SoftAssertions;

@Test
void shouldMapAllFieldsCorrectly() {
    ProductResponse response = service.getProduct(id);

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(response.getId()).isEqualTo("123");
    softly.assertThat(response.getName()).isEqualTo("Product");
    softly.assertThat(response.getPrice()).isEqualTo(BigDecimal.valueOf(99.99));
    softly.assertAll();  // Reports all failures at once
}

// Or with JUnit 5 extension
@ExtendWith(SoftAssertionsExtension.class)
class MyTest {
    @Test
    void test(SoftAssertions softly) {
        softly.assertThat(a).isEqualTo(1);
        softly.assertThat(b).isEqualTo(2);
        // assertAll() called automatically
    }
}
```

## Chained Assertions

```java
assertThat(result)
    .isNotNull()
    .hasSize(2)
    .extracting(Order::getStatus)
    .containsOnly(OrderStatus.CONFIRMED);

assertThat(savedOrder)
    .isNotNull()
    .satisfies(order -> {
        assertThat(order.getOrderNumber()).isNotNull();
        assertThat(order.getOrderLineItemsList()).hasSize(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    });
```

## Optional Assertions

```java
assertThat(optionalOrder).isPresent();
assertThat(optionalOrder).isEmpty();
assertThat(optionalOrder).isNotEmpty();

assertThat(optionalOrder)
    .isPresent()
    .hasValueSatisfying(order -> {
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    });

// Get value and continue asserting
assertThat(optionalOrder.get().getOrderNumber()).isEqualTo("123");
```

## Map Assertions

```java
assertThat(map).isEmpty();
assertThat(map).hasSize(3);
assertThat(map).containsKey("key");
assertThat(map).containsKeys("key1", "key2");
assertThat(map).containsValue("value");
assertThat(map).containsEntry("key", "value");
assertThat(map).doesNotContainKey("forbidden");
```
