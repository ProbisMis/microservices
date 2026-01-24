package com.demo.programming.order_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Resilient Feign Client with Circuit Breaker, Retry, and Bulkhead patterns.
 *
 * STUDY NOTES - Resilience4j Patterns:
 *
 * 1. CIRCUIT BREAKER
 *    Purpose: Stop calling a failing service to let it recover
 *    States:
 *      CLOSED → Normal operation, calls go through
 *      OPEN   → Service is failing, all calls fail-fast (no actual call made)
 *      HALF_OPEN → Testing if service recovered, limited calls allowed
 *
 * 2. RETRY
 *    Purpose: Automatically retry failed calls
 *    Use for: Transient failures (network glitches, temporary unavailability)
 *    Don't use for: Business logic failures (validation errors, not found)
 *
 * 3. BULKHEAD
 *    Purpose: Limit concurrent calls to prevent resource exhaustion
 *    Types:
 *      - Semaphore (default): Limits concurrent threads
 *      - ThreadPool: Dedicated thread pool for the call
 *
 * Order of decorators: Retry(CircuitBreaker(Bulkhead(actual call)))
 * - Bulkhead limits concurrent calls
 * - Circuit breaker tracks failures
 * - Retry wraps the circuit breaker
 */
@FeignClient(name = "inventory-service", url = "${inventory.service.url:}")
public interface ResilientInventoryClient {

    /**
     * Resilient inventory check with all three patterns.
     *
     * STUDY NOTE: Annotation order doesn't matter - Resilience4j applies
     * them in the correct order automatically.
     */
    @GetMapping("/api/inventory/check")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "isInStockFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    List<InventoryResponse> isInStock(@RequestParam("sku-code") List<String> skuCode);

    /**
     * Fallback method when circuit is OPEN or all retries exhausted.
     *
     * STUDY NOTES:
     * - Method signature must match original, plus Throwable parameter
     * - Can have multiple fallback methods for different exception types
     * - Fallback should be quick and not call other services
     *
     * Common fallback strategies:
     * 1. Return cached data
     * 2. Return default/safe value
     * 3. Return degraded response
     * 4. Throw custom exception for upstream handling
     */
    default List<InventoryResponse> isInStockFallback(List<String> skuCode, Throwable t) {
        // Log for monitoring - this helps identify when fallback is being used
        // In real implementation, inject a logger via @Slf4j on a wrapper class

        // Conservative approach: assume out of stock (fail safe)
        // This prevents overselling when inventory service is down
        return skuCode.stream()
                .map(sku -> new InventoryResponse(sku, false, 0))
                .toList();
    }
}


/**
 * STUDY NOTE: Required application.yml configuration
 *
 * resilience4j:
 *   circuitbreaker:
 *     instances:
 *       inventoryService:
 *         registerHealthIndicator: true          # Expose in /actuator/health
 *         slidingWindowType: COUNT_BASED         # or TIME_BASED
 *         slidingWindowSize: 10                  # Last N calls for failure rate
 *         minimumNumberOfCalls: 5                # Min calls before circuit opens
 *         failureRateThreshold: 50               # % failures to open circuit
 *         waitDurationInOpenState: 10s           # Time before HALF_OPEN
 *         permittedNumberOfCallsInHalfOpenState: 3  # Test calls in HALF_OPEN
 *         slowCallDurationThreshold: 2s          # What counts as "slow"
 *         slowCallRateThreshold: 100             # % slow calls to open circuit
 *
 *   retry:
 *     instances:
 *       inventoryService:
 *         maxAttempts: 3                         # Total attempts (including first)
 *         waitDuration: 500ms                    # Wait between retries
 *         enableExponentialBackoff: true         # Double wait time each retry
 *         exponentialBackoffMultiplier: 2
 *         retryExceptions:                       # Only retry these exceptions
 *           - java.io.IOException
 *           - java.util.concurrent.TimeoutException
 *           - feign.FeignException.ServiceUnavailable
 *         ignoreExceptions:                      # Never retry these
 *           - java.lang.IllegalArgumentException
 *
 *   bulkhead:
 *     instances:
 *       inventoryService:
 *         maxConcurrentCalls: 10                 # Max parallel calls
 *         maxWaitDuration: 100ms                 # Wait for slot before failing
 */


/**
 * STUDY NOTE: Alternative - Programmatic Circuit Breaker
 *
 * Sometimes you need more control than annotations provide.
 * Here's how to use Resilience4j programmatically:
 */
// @Service
// @RequiredArgsConstructor
// @Slf4j
// public class ResilientInventoryService {
//
//     private final InventoryClient inventoryClient;
//     private final CircuitBreakerRegistry circuitBreakerRegistry;
//
//     public List<InventoryResponse> checkInventory(List<String> skuCodes) {
//         CircuitBreaker circuitBreaker = circuitBreakerRegistry
//             .circuitBreaker("inventoryService");
//
//         // Decorate the call
//         Supplier<List<InventoryResponse>> decoratedSupplier = CircuitBreaker
//             .decorateSupplier(circuitBreaker, () -> inventoryClient.isInStock(skuCodes));
//
//         // Execute with fallback
//         return Try.ofSupplier(decoratedSupplier)
//             .recover(throwable -> {
//                 log.warn("Circuit breaker fallback: {}", throwable.getMessage());
//                 return skuCodes.stream()
//                     .map(sku -> new InventoryResponse(sku, false, 0))
//                     .toList();
//             })
//             .get();
//     }
// }


/**
 * STUDY NOTE: Circuit Breaker Events for Monitoring
 *
 * Register event listeners for observability:
 */
// @Configuration
// public class CircuitBreakerConfig {
//
//     @Bean
//     public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
//         return new RegistryEventConsumer<>() {
//             @Override
//             public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
//                 event.getAddedEntry().getEventPublisher()
//                     .onStateTransition(e -> log.info(
//                         "Circuit breaker {} changed state: {} -> {}",
//                         e.getCircuitBreakerName(),
//                         e.getStateTransition().getFromState(),
//                         e.getStateTransition().getToState()
//                     ));
//             }
//         };
//     }
// }


/**
 * Enhanced InventoryResponse for the examples above.
 */
record InventoryResponse(String skuCode, boolean inStock, int quantity) {}
