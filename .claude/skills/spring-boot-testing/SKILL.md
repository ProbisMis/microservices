---
name: spring-boot-testing
description: >
  Guide for writing tests in Spring Boot microservices. Use when writing unit tests,
  integration tests, or when user mentions JUnit, Mockito, test coverage, or asks
  how to test a service/controller/repository.
---

# Spring Boot Testing Guide

Quick reference for JUnit 5, Mockito, and AssertJ patterns used in this codebase.

## Quick Templates

### Unit Test Class
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private MyRepository repository;

    @InjectMocks
    private MyService service;

    @Nested
    @DisplayName("methodName - Success Scenarios")
    class MethodNameSuccessTests {
        @Test
        @DisplayName("Should do something when condition is met")
        void shouldDoSomethingWhenConditionMet() {
            // Given
            when(repository.findById(any())).thenReturn(Optional.of(entity));

            // When
            Result result = service.method(input);

            // Then
            assertThat(result).isNotNull();
            verify(repository).save(any());
        }
    }
}
```

### Integration Test Class
```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"topic.name"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MyKafkaIntegrationTest {
    // See references/integration-tests.md
}
```

## Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Test class | `{ClassName}Test` | `OrderServiceTest` |
| Nested class | `{MethodName}{Scenario}Tests` | `PlaceOrderSuccessTests` |
| Test method | `should{Expected}When{Condition}` | `shouldThrowExceptionWhenItemOutOfStock` |
| DisplayName | Human-readable description | `"Should place order successfully"` |

## Test Categories

| Type | Annotations | Purpose |
|------|-------------|---------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Test single class with mocked dependencies |
| Integration | `@SpringBootTest` | Test with real Spring context |
| Kafka | `@EmbeddedKafka` | Test Kafka producers/consumers |

## Quality Checklist

- [ ] Tests organized with `@Nested` classes
- [ ] Clear `@DisplayName` on classes and methods
- [ ] Given-When-Then comments in test body
- [ ] AssertJ assertions (not JUnit assertions)
- [ ] `verify()` for side effects
- [ ] Edge cases covered (null, empty, boundary values)
- [ ] No test interdependence

## Reference Files

- [Unit Test Patterns](references/unit-tests.md) - Mockito stubbing, verification, ArgumentCaptor
- [Integration Tests](references/integration-tests.md) - EmbeddedKafka, Awaitility, TestContainers
- [AssertJ Assertions](references/assertions.md) - Fluent assertion patterns
