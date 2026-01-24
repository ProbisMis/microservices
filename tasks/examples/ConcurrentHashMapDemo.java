package com.demo.programming.study;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentHashMap Deep Dive - Thread Safety Study
 *
 * Run this class to see thread safety in action.
 */
public class ConcurrentHashMapDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ConcurrentHashMap Study Demo ===\n");

        demonstrateRaceCondition();
        System.out.println("\n" + "=".repeat(50) + "\n");

        demonstrateConcurrentHashMap();
        System.out.println("\n" + "=".repeat(50) + "\n");

        demonstrateAtomicOperations();
        System.out.println("\n" + "=".repeat(50) + "\n");

        demonstrateComputeIfAbsent();
    }

    /**
     * DEMO 1: Race Condition with HashMap
     *
     * This demonstrates why HashMap is NOT thread-safe.
     * Multiple threads incrementing the same counter will lose updates.
     */
    static void demonstrateRaceCondition() throws InterruptedException {
        System.out.println("DEMO 1: Race Condition with HashMap");
        System.out.println("-----------------------------------");

        Map<String, Integer> unsafeMap = new HashMap<>();
        unsafeMap.put("counter", 0);

        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // RACE CONDITION: get, increment, put is not atomic
                    int current = unsafeMap.get("counter");
                    unsafeMap.put("counter", current + 1);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int expected = threadCount * incrementsPerThread;
        int actual = unsafeMap.get("counter");

        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
        System.out.println("Lost updates: " + (expected - actual));
        System.out.println("⚠️  HashMap is NOT thread-safe!");
    }

    /**
     * DEMO 2: ConcurrentHashMap with atomic operations
     *
     * Using compute() ensures atomic read-modify-write.
     */
    static void demonstrateConcurrentHashMap() throws InterruptedException {
        System.out.println("DEMO 2: ConcurrentHashMap with atomic operations");
        System.out.println("------------------------------------------------");

        ConcurrentHashMap<String, Integer> safeMap = new ConcurrentHashMap<>();
        safeMap.put("counter", 0);

        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // ATOMIC: compute() locks the bucket during operation
                    safeMap.compute("counter", (key, value) -> value + 1);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int expected = threadCount * incrementsPerThread;
        int actual = safeMap.get("counter");

        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
        System.out.println("✅ ConcurrentHashMap with compute() is thread-safe!");
    }

    /**
     * DEMO 3: Atomic Operation Patterns
     *
     * Common thread-safe patterns with ConcurrentHashMap.
     */
    static void demonstrateAtomicOperations() {
        System.out.println("DEMO 3: Atomic Operation Patterns");
        System.out.println("----------------------------------");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // Pattern 1: putIfAbsent - Only puts if key doesn't exist
        map.putIfAbsent("visits", 0);
        System.out.println("After putIfAbsent: " + map.get("visits"));

        // Pattern 2: compute - Atomic read-modify-write
        map.compute("visits", (k, v) -> v + 1);
        System.out.println("After compute: " + map.get("visits"));

        // Pattern 3: merge - Atomic update with remapping function
        // Perfect for counters: if key exists, apply function; otherwise use value
        map.merge("visits", 1, Integer::sum);
        System.out.println("After merge: " + map.get("visits"));

        // Pattern 4: getOrDefault - Safe read with default
        int errors = map.getOrDefault("errors", 0);
        System.out.println("getOrDefault for missing key: " + errors);

        System.out.println("\n✅ All operations are atomic and thread-safe!");
    }

    /**
     * DEMO 4: computeIfAbsent for Lazy Initialization
     *
     * Perfect for caching expensive computations.
     */
    static void demonstrateComputeIfAbsent() throws InterruptedException {
        System.out.println("DEMO 4: computeIfAbsent for Caching");
        System.out.println("-----------------------------------");

        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger computationCount = new AtomicInteger(0);

        // Simulate expensive computation
        var expensiveComputation = (String key) -> {
            computationCount.incrementAndGet();
            System.out.println("  Computing value for: " + key);
            try {
                Thread.sleep(100); // Simulate slow operation
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "RESULT_FOR_" + key;
        };

        // Multiple threads requesting same key simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                // computeIfAbsent ensures computation happens ONLY ONCE
                String result = cache.computeIfAbsent("expensive-key",
                        expensiveComputation::apply);
                System.out.println("  Thread got: " + result);
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\nTotal computations performed: " + computationCount.get());
        System.out.println("✅ Expensive operation ran only ONCE despite 5 threads!");
    }
}


/**
 * STUDY NOTES: ConcurrentHashMap Internals (Java 8+)
 *
 * 1. STRUCTURE
 *    - Array of Nodes (buckets)
 *    - Each bucket: linked list OR red-black tree (when > 8 entries)
 *    - No global lock - uses per-bucket synchronization
 *
 * 2. LOCK-FREE READS
 *    - Volatile read of Node.val
 *    - No synchronization needed
 *    - Allows high read concurrency
 *
 * 3. FINE-GRAINED WRITES
 *    - CAS (Compare-And-Swap) for inserting into empty bucket
 *    - synchronized block on first Node for updates
 *    - Only locks the affected bucket, not the entire map
 *
 * 4. KEY METHODS
 *
 *    | Method | Thread-Safe? | Use Case |
 *    |--------|--------------|----------|
 *    | get() | Yes (lock-free) | Simple read |
 *    | put() | Yes | Simple write |
 *    | putIfAbsent() | Yes (atomic) | Insert if missing |
 *    | compute() | Yes (atomic) | Read-modify-write |
 *    | computeIfAbsent() | Yes (atomic) | Lazy initialization, caching |
 *    | merge() | Yes (atomic) | Counters, combining values |
 *    | remove() | Yes (atomic) | Conditional removal |
 *
 * 5. COMMON ANTIPATTERNS
 *
 *    // WRONG: Check-then-act race condition
 *    if (!map.containsKey(key)) {
 *        map.put(key, computeValue());  // Another thread might put between!
 *    }
 *
 *    // RIGHT: Atomic operation
 *    map.computeIfAbsent(key, k -> computeValue());
 *
 *    // WRONG: Non-atomic increment
 *    map.put(key, map.get(key) + 1);
 *
 *    // RIGHT: Atomic increment
 *    map.compute(key, (k, v) -> v == null ? 1 : v + 1);
 *    // or
 *    map.merge(key, 1, Integer::sum);
 *
 * 6. INTERVIEW QUESTIONS
 *
 *    Q: How does ConcurrentHashMap differ from Hashtable?
 *    A: Hashtable synchronizes every method (coarse-grained).
 *       ConcurrentHashMap uses per-bucket locks (fine-grained).
 *       ConcurrentHashMap allows concurrent reads without locking.
 *
 *    Q: Can ConcurrentHashMap have null keys or values?
 *    A: No! Both are prohibited. Use Optional or sentinel values instead.
 *       Reason: null is ambiguous (missing key vs. null value).
 *
 *    Q: What's the initial capacity and load factor?
 *    A: Default initial capacity: 16
 *       Default load factor: 0.75
 *       Resize threshold: capacity * load factor = 12
 *
 *    Q: When does a bucket convert to a tree?
 *    A: When bucket has > 8 entries (TREEIFY_THRESHOLD)
 *       Converts back to list when < 6 entries (UNTREEIFY_THRESHOLD)
 */
