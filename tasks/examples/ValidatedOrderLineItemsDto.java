package com.demo.programming.order_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Enhanced OrderLineItemsDto with JSR-303 Bean Validation.
 *
 * STUDY NOTES - Common Validation Annotations:
 *
 * String validations:
 *   @NotNull      - Cannot be null
 *   @NotBlank     - Not null, not empty, not whitespace only
 *   @NotEmpty     - Not null, not empty (works on collections too)
 *   @Size(min, max) - String length constraints
 *   @Pattern(regexp) - Regex matching
 *   @Email        - Valid email format
 *
 * Number validations:
 *   @Min(value)   - Minimum value
 *   @Max(value)   - Maximum value
 *   @Positive     - Must be > 0
 *   @PositiveOrZero - Must be >= 0
 *   @Negative     - Must be < 0
 *   @DecimalMin/@DecimalMax - For BigDecimal
 *
 * Other:
 *   @Past/@Future - Date constraints
 *   @Valid        - Cascades validation to nested objects
 *
 * Activation: Add @Valid to controller method parameter:
 *   public void create(@Valid @RequestBody OrderRequest request)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Details of an order line item")
public class ValidatedOrderLineItemsDto {

    @Schema(description = "Unique identifier of the line item", example = "1")
    private Long id;

    /**
     * STUDY NOTE: @NotBlank is for Strings only.
     * - Rejects null
     * - Rejects empty string ""
     * - Rejects whitespace-only "   "
     *
     * Use @NotNull for non-String types.
     */
    @NotBlank(message = "SKU code is required")
    @Size(min = 3, max = 50, message = "SKU code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$",
            message = "SKU code must contain only uppercase letters, numbers, underscores, and hyphens")
    @Schema(description = "Stock Keeping Unit code of the product", example = "IPHONE_15_PRO")
    private String skuCode;

    /**
     * STUDY NOTE: @Positive is cleaner than @Min(1) for monetary values.
     * Clearly expresses business intent.
     */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Schema(description = "Price of the product", example = "999.99")
    private Double price;

    /**
     * STUDY NOTE: @Min/@Max allow custom messages and specific bounds.
     * Use when you need to express business limits.
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity cannot exceed 1000 per line item")
    @Schema(description = "Quantity ordered", example = "2")
    private Integer quantity;
}


/**
 * STUDY NOTE: Example of validated request with nested validation.
 *
 * Key point: Use @Valid on the List to cascade validation to each element.
 */
// @Data
// @AllArgsConstructor
// @NoArgsConstructor
// public class ValidatedOrderRequest {
//
//     /**
//      * @NotEmpty ensures the list is not null and not empty.
//      * @Valid cascades validation to each OrderLineItemsDto in the list.
//      * Without @Valid, the nested objects would NOT be validated!
//      */
//     @NotEmpty(message = "Order must contain at least one item")
//     @Size(max = 100, message = "Order cannot contain more than 100 items")
//     @Valid  // CRITICAL: Without this, nested validation is skipped!
//     private List<ValidatedOrderLineItemsDto> orderLineItemsDtoList;
// }


/**
 * STUDY NOTE: Example controller showing @Valid usage.
 *
 * When validation fails:
 * 1. Spring throws MethodArgumentNotValidException
 * 2. Your GlobalExceptionHandler catches it
 * 3. Client receives structured error response with field-level messages
 */
// @PostMapping
// public ResponseEntity<String> placeOrder(
//         @Valid @RequestBody ValidatedOrderRequest request) {
//     // If we reach here, validation passed!
//     orderService.placeOrder(request);
//     return ResponseEntity.status(HttpStatus.CREATED).body("Order placed successfully!");
// }
