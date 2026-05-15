# Implementation Plan: Dynamic Order Processing Service

## Overview

Implementation follows the hexagonal layering defined in `design.md` (api / application / domain / infrastructure) on Java 21 + Spring Boot, built with Maven. Tasks are sequenced so that the pure domain (records, state machine, pricing strategies, validation chain, notification dispatcher) is built and unit-tested first, then infrastructure adapters (JPA, Redis, HTTP, Resilience4j) plug into the domain ports, and finally the API layer is wired through the `OrderService` facade.

Property-based tests (jqwik) are written close to the production code they validate (per the design's correctness properties — pricing invariants, lifecycle invariants, serialization round-trip, cache idempotence) so contradictions surface early. JaCoCo enforces 90% line / 80% branch coverage at the `verify` phase, per the engineering standards.

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Make sure that each prompt builds on the previous prompts, and ends with wiring things together. There should be no hanging or orphaned code that isn't integrated into a previous step. Focus ONLY on tasks that involve writing, modifying, or testing code.

## Tasks

- [x] 1. Set up Maven project skeleton, dependencies, and quality gates
  - [x] 1.1 Create `pom.xml` with Spring Boot parent, Java 21, and core dependencies
    - Spring Boot starters: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`
    - Resilience4j: `resilience4j-spring-boot3`, `resilience4j-micrometer`
    - Test dependencies: `spring-boot-starter-test`, `net.jqwik:jqwik`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`
    - Configure `maven-compiler-plugin` for Java 21 with `--enable-preview` only if needed
    - _Requirements: 11, 13, 14, all_
  - [x] 1.2 Configure JaCoCo plugin with coverage thresholds bound to `verify`
    - Add `jacoco-maven-plugin` with `prepare-agent`, `report`, and `check` executions
    - Set BUNDLE rule: line coverage >= 0.90, branch coverage >= 0.80
    - Add explicit, justified excludes: `OrderProcessingApplication`, `**/dto/**` records, configuration-only classes
    - Bind `check` to the `verify` phase so `mvn clean verify` fails on threshold breach
    - _Requirements: engineering-standards (coverage)_
  - [x] 1.3 Create standard Maven directory layout and bootstrap classes
    - Create `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`
    - Create `OrderProcessingApplication.java` with `@SpringBootApplication`
    - Create `application.yml` with `spring.threads.virtual.enabled=true` placeholder, datasource, Redis, and Resilience4j config sections
    - Create `application-test.yml` for test profile
    - _Requirements: 11.1, engineering-standards (layout)_
  - [x] 1.4 Verify build skeleton compiles and runs
    - Run `mvn clean verify` (will pass with no production code or with placeholder smoke test)
    - Add a single smoke test asserting Spring context loads to satisfy initial JaCoCo run
    - _Requirements: engineering-standards (build)_

- [x] 2. Implement core domain value objects (immutable, no Spring)
  - [x] 2.1 Create primitive value records: `OrderId`, `Sku`, `IdempotencyKey`, `Money`
    - `Money` enforces non-null currency, scale <= 4, provides `plus`/`minus` requiring matching currency
    - Throw `IllegalArgumentException` on invalid construction
    - _Requirements: 3.4, 3.5, 18.1_
  - [x] 2.2 Create `OrderItem` record with quantity >= 1 invariant
    - Compact constructor rejects quantity < 1
    - _Requirements: 1.2_
  - [x] 2.3 Create `OrderStatus` enum with `isTerminal()` (DELIVERED, CANCELLED, FAILED)
    - _Requirements: 6.4_
  - [x] 2.4 Create `OrderStatusEvent` record (orderId, from, to, at, actor, reason)
    - _Requirements: 6.3, 8.4_
  - [x] 2.5 Write unit tests for value object invariants
    - Test `Money` rejects mismatched currency in `plus`/`minus`, scale > 4
    - Test `OrderItem` rejects quantity < 1
    - Test `OrderStatus.isTerminal()` for every value
    - _Requirements: 1.2, 3.5, 6.4_

- [x] 3. Implement `Order` aggregate with Builder
  - [x] 3.1 Create immutable `Order` class with package-private constructor
    - Fields: id, items, status, subtotal, discountTotal, taxTotal, shippingTotal, grandTotal, idempotencyKey, createdAt, updatedAt, failureReason
    - `withStatus`, `withTotals`, `withFailure` return new `Order` instances
    - _Requirements: 1.1, 3.3, 6.1_
  - [x] 3.2 Create `OrderBuilder` (Builder pattern) enforcing required fields and defaults
    - `build()` validates required fields and initial status = CREATED
    - _Requirements: 1.1, 13.1_
  - [x] 3.3 Write unit tests for `Order` immutability and `OrderBuilder` invariants
    - Test `withStatus` returns a new instance and leaves the original unchanged
    - Test builder fails when required fields are missing
    - _Requirements: 1.1, 13.1_

- [x] 4. Implement order lifecycle state machine (State pattern)
  - [x] 4.1 Create `OrderState` interface and per-status state classes
    - One class per status under `domain.lifecycle.states`: `CreatedState`, `ValidatedState`, `PricedState`, `ReservedState`, `ConfirmedState`, `ShippedState`, `DeliveredState`, `CancelledState`, `FailedState`
    - Each state declares its allowed outgoing transitions per Req 6.1
    - _Requirements: 6.1, 13.3_
  - [x] 4.2 Create `OrderStateMachine.assertTransition(from, to)` guard
    - Reject any edge not in Req 6.1
    - Reject all outgoing transitions from terminal states
    - _Requirements: 6.1, 6.2, 6.4_
  - [x] 4.3 Write property test for lifecycle transitions
    - **Property 1: Only graph-defined transitions are accepted**
    - Generate random (from, to) pairs over `OrderStatus`; assert `assertTransition` accepts iff edge is in the Req 6.1 graph
    - **Validates: Requirements 6.1, 6.2**
  - [x] 4.4 Write property test for terminal-state immutability
    - **Property 2: No transition is permitted out of a terminal state**
    - For all terminal states (DELIVERED, CANCELLED, FAILED), `assertTransition(terminal, any)` throws
    - **Validates: Requirements 6.4**

- [x] 5. Implement validation chain (Chain of Responsibility)
  - [x] 5.1 Create `ValidationRule` interface and `ValidationResult` value
    - Each rule returns pass/fail with stable rule id and message
    - _Requirements: 2.1, 14.2_
  - [x] 5.2 Implement individual rules under `domain.validation.rules`
    - `NonEmptyItemsRule`, `PositiveQuantityRule`, `KnownSkuRule`, `IdempotencyRule`
    - _Requirements: 1.2, 2.1, 2.3_
  - [x] 5.3 Implement `ValidationChain` orchestrator
    - Runs every registered rule; first failure aggregates to a FAILED outcome with rule id and message persisted on the order
    - New rules are added by registering a new `ValidationRule` bean (OCP)
    - _Requirements: 2.2, 2.3, 2.4, 14.2_
  - [x] 5.4 Write unit tests for each validation rule
    - Cover passing and failing inputs, including boundary quantity (0, 1)
    - _Requirements: 1.2, 2.1, 2.3_
  - [x] 5.5 Write unit test for `ValidationChain` open/closed extension
    - Add a new rule at runtime and assert the chain invokes it without modifying the chain class
    - _Requirements: 2.4, 14.2_

- [x] 6. Implement pricing engine (Strategy + Factory Method)
  - [x] 6.1 Create `PricingStrategy` interface returning a `PriceContribution`
    - LSP: every implementation honors non-negative subtotal/tax/shipping postconditions
    - _Requirements: 3.2, 3.5, 13.4, 14.2_
  - [x] 6.2 Implement strategies under `domain.pricing.strategies`
    - `DiscountStrategy`, `TaxStrategy`, `ShippingStrategy`, `NoOpStrategy`
    - Each declares `isIdempotent()` for Property 4
    - _Requirements: 3.1, 3.2, 20.3_
  - [x] 6.3 Implement `PricingStrategyChainFactory` (Factory Method)
    - Builds an ordered `List<PricingStrategy>` from a profile name (e.g., `default`)
    - Throws on missing profile so `OrderService` can transition the order to FAILED with a pricing-error reason
    - _Requirements: 3.1, 3.6, 13.1_
  - [x] 6.4 Implement `PricingEngine.price(Order, profile)`
    - Applies the chain in order; computes subtotal, discountTotal, taxTotal, shippingTotal, grandTotal
    - Persists totals on the resulting `Order` and transitions VALIDATED → PRICED
    - _Requirements: 3.1, 3.3, 3.4_
  - [x] 6.5 Write property test for pricing total invariant
    - **Property 3: grand_total = subtotal - discountTotal + taxTotal + shippingTotal**
    - **Validates: Requirements 3.4**
  - [x] 6.6 Write property test for pricing non-negativity
    - **Property 4: For all orders and chains, subtotal, taxTotal, shippingTotal >= 0 and grandTotal >= 0**
    - **Validates: Requirements 3.5, 20.1**
  - [x] 6.7 Write property test for no-op chain identity
    - **Property 5: Applying a chain of only no-op strategies yields grandTotal == subtotal**
    - **Validates: Requirements 20.2**
  - [x] 6.8 Write property test for idempotent chain stability
    - **Property 6: When every strategy declares `isIdempotent()`, applying the chain twice yields the same grandTotal as applying it once**
    - **Validates: Requirements 20.3**
  - [x] 6.9 Write unit tests for `PricingEngine` failure path
    - Missing profile triggers FAILED transition with pricing-error reason
    - _Requirements: 3.6_

- [x] 7. Implement notification dispatcher (Observer)
  - [x] 7.1 Create `OrderEventListener` interface and `NotificationDispatcher` (Subject)
    - Dispatcher accepts registered listeners; new channels added by registering a new bean (OCP)
    - On listener exception, dispatcher records the failure and continues to remaining listeners
    - _Requirements: 8.2, 8.3, 13.3, 13.6_
  - [x] 7.2 Define `NotificationChannel` enum and event payload
    - Payload includes Order_ID, new Order_Status, transition timestamp
    - _Requirements: 8.1_
  - [x] 7.3 Write unit tests for dispatcher fan-out and isolation
    - Stubbed listeners; one throws; assert remaining receive the event and the failure is recorded
    - _Requirements: 8.2, 8.3_

- [x] 8. Define domain ports (Dependency Inversion seams)
  - [x] 8.1 Create port interfaces under `domain.port`
    - `OrderRepository`, `OrderCachePort`, `InventoryPort`, `PaymentPort`, `IdempotencyStore`
    - `OrderCachePort.isAvailable()` supports cache-degraded path
    - _Requirements: 9, 10, 14.3_
  - [x] 8.2 Define result types: `ReservationResult`, `AuthorizationResult`
    - Capture success, out-of-stock SKUs, and decline reasons
    - _Requirements: 4.3, 5.3_

- [x] 9. Implement persistence adapter (JPA)
  - [x] 9.1 Create JPA entities
    - `OrderJpaEntity` (with `@Version` for optimistic concurrency), `OrderItemJpaEntity`, `OrderStatusEventJpaEntity`, `IdempotencyRecordJpaEntity`
    - Unique constraint on `idempotency_key`
    - _Requirements: 1.4, 6.3, 11.3_
  - [x] 9.2 Implement `JpaOrderRepository` adapting to `OrderRepository`
    - `save` upserts the order and items in a single transaction
    - `appendStatusEvent` persists the event in the same transaction as the status update
    - `search` paginates by status, customerId, time range
    - _Requirements: 1.4, 6.3, 8.4, 9.1_
  - [x] 9.3 Implement `JpaIdempotencyStore` adapting to `IdempotencyStore`
    - `findExisting` and `register` for deduplication of order creation
    - _Requirements: 1.3_
  - [x] 9.4 Write `@DataJpaTest` slice tests for repository and idempotency store
    - Covers upsert, status-event append, idempotency uniqueness
    - _Requirements: 1.3, 1.4, 6.3_

- [x] 10. Implement cache adapter (Decorator + Redis)
  - [x] 10.1 Configure `RedisTemplate` and TTL via `CacheConfig`
    - Key schema: `order:v1:{orderId}`; TTL configurable via `app.cache.order.ttl` (default 5m)
    - _Requirements: 10.1, 10.2_
  - [x] 10.2 Implement `RedisOrderCache` adapting to `OrderCachePort`
    - `put` is idempotent on identical state; `evict` on update; `isAvailable()` reflects cache-breaker state
    - _Requirements: 9.4, 9.5, 10.3, 19.1_
  - [x] 10.3 Implement `CachingOrderRepository` (Decorator over `JpaOrderRepository`)
    - Read-through on miss, populate cache before returning; evict on writes; bypass cache when `isAvailable()` is false and record `cache_degraded` event
    - _Requirements: 9.3, 9.4, 9.5, 10.3, 13.2_
  - [x] 10.4 Write property test for cache-population idempotence
    - **Property 7: Repeated `put(orderId, orderState)` with identical state leaves the cache observably unchanged**
    - **Validates: Requirements 19.1**
  - [x] 10.5 Write `@DataRedisTest` for `RedisOrderCache` round-trip
    - Put/get/evict and TTL behavior
    - _Requirements: 10.1, 10.2_

- [x] 11. Implement order serialization with round-trip property
  - [x] 11.1 Create JSON serializer/deserializer for `Order` shared by API and cache
    - Use Jackson with explicit `Order` ↔ `OrderResponse` mapping; reject unknown/missing required fields
    - _Requirements: 18.1, 18.2, 18.4_
  - [x] 11.2 Write property test for serialization round-trip
    - **Property 8: For all valid Orders, `deserialize(serialize(o))` is semantically equivalent to `o`**
    - **Validates: Requirements 18.3**
  - [x] 11.3 Write unit test for malformed JSON rejection
    - Missing required field and bad type produce a descriptive error and no partial Order
    - _Requirements: 18.4_

- [x] 12. Implement inventory adapter (Adapter + Resilience4j)
  - [x] 12.1 Implement `HttpInventoryAdapter` adapting to `InventoryPort`
    - Translates external/mock HTTP/JSON to `ReservationResult`; supports `reserve` and `release`
    - _Requirements: 4.1, 4.2, 4.3, 7.2, 13.5_
  - [x] 12.2 Annotate adapter calls with `@CircuitBreaker(name="inventory")`
    - On `CallNotPermittedException`, the orchestrator transitions order to FAILED with `dependency_unavailable:inventory`
    - _Requirements: 4.4, 4.5, 12.1, 12.3_
  - [x] 12.3 Write unit tests for `HttpInventoryAdapter` translations
    - Mock HTTP client; cover success, out-of-stock, transport failure
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 13. Implement payment adapter (Adapter + Resilience4j)
  - [x] 13.1 Implement `HttpPaymentAdapter` adapting to `PaymentPort`
    - `authorize(orderId, grandTotal)` and `voidAuthorization(orderId)` against external/mock
    - _Requirements: 5.1, 5.2, 5.3, 13.5_
  - [x] 13.2 Annotate adapter calls with `@CircuitBreaker(name="payment")`
    - On `CallNotPermittedException`, orchestrator transitions order to FAILED with `dependency_unavailable:payment`
    - _Requirements: 5.4, 5.5, 12.1, 12.3_
  - [x] 13.3 Write unit tests for `HttpPaymentAdapter` translations
    - Cover authorization success, decline reason, transport failure
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 14. Configure Resilience4j circuit breakers and shared config
  - [x] 14.1 Add Resilience4j instance configs in `application.yml`
    - Instances: `inventory`, `payment`, `cache` per design (window, thresholds, durations)
    - _Requirements: 12.2, 12.4, 12.5, 12.6_
  - [x] 14.2 Wire breaker state observation for cache adapter
    - `OrderCachePort.isAvailable()` returns false while `cache` breaker is OPEN
    - _Requirements: 10.3, 12.3_
  - [x] 14.3 Write integration test for breaker open → half-open → closed transitions
    - Use Resilience4j test support; simulate failures past threshold and verify state transitions
    - _Requirements: 12.2, 12.4, 12.5, 12.6_

- [x] 15. Configure virtual-thread executors and per-Order concurrency
  - [x] 15.1 Enable virtual threads for HTTP and create `pipelineExecutor` bean
    - Set `spring.threads.virtual.enabled=true`; expose `Executor pipelineExecutor()` returning `Executors.newVirtualThreadPerTaskExecutor()`
    - _Requirements: 11.1, 11.2_
  - [x] 15.2 Implement `OrderLockRegistry` with per-`OrderId` `ReentrantLock` stripes
    - `withLock(orderId, body)` acquires/releases lock; reads do not take the lock
    - _Requirements: 11.3, 11.4_
  - [x] 15.3 Write property test for per-Order serialization invariant
    - **Property 9: Concurrent state-mutating operations on the same `OrderId` produce a final status reachable by some sequential ordering of the inputs (no out-of-lifecycle interleavings)**
    - Use jqwik with concurrent runners against an in-memory repository
    - **Validates: Requirements 11.3, 11.4**

- [x] 16. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 17. Implement application orchestration (Template Method + Command + Facade)
  - [x] 17.1 Implement `OrderProcessingPipeline` (Template Method)
    - Fixed sequence: validate → price → reserve → pay → notify
    - Stage methods delegate to `ValidationChain`, `PricingEngine`, `InventoryPort`, `PaymentPort`, `NotificationDispatcher`
    - _Requirements: 2.1, 3.1, 4.1, 5.1, 8.1, 13.3_
  - [x] 17.2 Implement command classes under `application.command`
    - `OrderCommand` interface with `CreateOrderCommand`, `CancelOrderCommand`, `TransitionOrderCommand`
    - Each command runs under `OrderLockRegistry.withLock(orderId, ...)` and records an `OrderStatusEvent`
    - _Requirements: 6.3, 7.1, 7.2, 7.3, 11.3, 13.3_
  - [x] 17.3 Implement `OrderService` Facade
    - `create`, `get`, `list`, `cancel`, `events`
    - `create` enforces idempotency via `IdempotencyStore`, delegates to pipeline, and ensures persistence before notification (Req 8.4)
    - `cancel` invokes `InventoryPort.release` when transitioning from RESERVED or CONFIRMED
    - _Requirements: 1.1, 1.3, 1.4, 7.1, 7.2, 7.3, 8.4, 9.1, 13.2_
  - [x] 17.4 Write unit tests for `OrderService` with mocked ports
    - Covers idempotency hit, validation failure → FAILED, pricing failure → FAILED, reservation success/failure, payment success/decline, cancel pre/post reserve, breaker-open → FAILED with dependency reason
    - _Requirements: 1.3, 2.3, 3.6, 4.2, 4.3, 4.5, 5.2, 5.3, 5.5, 7.1, 7.2_

- [x] 18. Implement REST API (controller, DTOs, error handler)
  - [x] 18.1 Create request/response DTOs under `api.dto`
    - `CreateOrderRequest`, `OrderResponse`, `OrderSummary`, `CancelRequest`, `OrderStatusEventResponse`, `ErrorResponse`
    - Use Bean Validation annotations on inbound DTOs
    - _Requirements: 1.1, 1.2, 9.1, 15.1_
  - [x] 18.2 Implement `OrderController` with endpoints from design
    - `POST /api/v1/orders` (with optional `Idempotency-Key` header), `GET /api/v1/orders/{id}`, `GET /api/v1/orders`, `POST /api/v1/orders/{id}/cancel`, `GET /api/v1/orders/{id}/events`
    - Returns `Location` header on create; maps domain errors to HTTP status codes per design
    - _Requirements: 1.1, 1.3, 7.1, 9.1, 9.2_
  - [x] 18.3 Implement `GlobalExceptionHandler` with stable error codes
    - 400 `VALIDATION_FAILED` (with field paths), 404 `ORDER_NOT_FOUND`, 409 `IDEMPOTENCY_CONFLICT` / `INVALID_TRANSITION`, 503 `DEPENDENCY_UNAVAILABLE` (with `dependency` field), 500 with correlation id
    - _Requirements: 6.2, 9.2, 12.3, 15.1, 15.2, 15.3, 15.4_
  - [x] 18.4 Write `@WebMvcTest` slice tests for `OrderController`
    - Happy paths and error paths for each endpoint, including idempotency-key replay returning the same Order_ID
    - _Requirements: 1.1, 1.3, 7.1, 7.3, 9.1, 9.2, 15.1, 15.2_

- [x] 19. Implement observability
  - [x] 19.1 Add structured log emission on every accepted state transition
    - Fields: orderId, fromStatus, toStatus, timestamp, correlationId, actor
    - Emit from `OrderService` or pipeline after persistence (Req 8.4 ordering)
    - _Requirements: 8.4, 16.1_
  - [x] 19.2 Configure Actuator health and metrics
    - Health: liveness, readiness checks for Cache_Layer and each External_Dependency
    - Metrics: HTTP request count/latency (via Micrometer), Resilience4j circuit-breaker state per dependency, cache hit/miss counters
    - _Requirements: 16.2, 16.3_
  - [x] 19.3 Write unit tests for the structured-log appender contract
    - Capture logs and assert required fields are present for every transition
    - _Requirements: 16.1_

- [x] 20. Wire end-to-end integration tests
  - [x] 20.1 Implement integration test covering full order lifecycle
    - Testcontainers Postgres + embedded/mock Redis; mocked HTTP inventory and payment
    - Drive: create → validate → price → reserve → pay → notify → ship → deliver, asserting events and cache population
    - _Requirements: 1.1, 2.2, 3.3, 4.2, 5.2, 6.1, 8.1, 9.3, 9.4_
  - [x] 20.2 Implement integration test for circuit-breaker open path
    - Force inventory adapter failures past threshold; assert subsequent orders transition to FAILED with `dependency_unavailable:inventory` and 503 surface
    - Repeat for payment dependency
    - _Requirements: 4.5, 5.5, 12.2, 12.3, 15.3_
  - [x] 20.3 Implement integration test for cache-degraded path
    - Force cache breaker OPEN; assert reads/writes still succeed against the primary store and `cache_degraded` event is recorded
    - _Requirements: 10.3_

- [x] 21. Author documentation deliverables
  - [x] 21.1 Write repository `README.md`
    - Purpose, build/run instructions (`mvn clean verify`, `mvn spring-boot:run`), configuration keys, API summary table
    - _Requirements: 17.1_
  - [x] 21.2 Write `docs/PATTERNS.md` (Pattern Inventory)
    - For each GoF pattern used, list FQN of implementing classes and the rationale; include all patterns from Req 13.1, 13.2, 13.3
    - _Requirements: 13.1, 13.2, 13.3, 17.2, 17.5_
  - [x] 21.3 Write `docs/REFACTORING_CASE_STUDY.md`
    - One-page case study: a tightly-coupled section (e.g., monolithic pricing or dispatcher) and the refactor applied via Strategy/Observer/Decorator
    - _Requirements: 17.3_
  - [x] 21.4 Add `docs/ARCHITECTURE.md` with Mermaid component diagram
    - Show data flow between Order_Service, Cache_Layer, External_Dependencies, and where Circuit_Breaker and Virtual_Thread_Executor are applied
    - _Requirements: 17.4_

- [x] 22. Final checkpoint - Ensure all tests pass and coverage thresholds hold
  - Run `mvn clean verify` and confirm JaCoCo gate (90% line / 80% branch) passes
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test or integration tasks and can be skipped for a faster MVP, but the engineering standards require them for the production target (90% line / 80% branch coverage and full PBT coverage).
- Each task references specific requirement clauses for traceability. Property tests are tagged with their property number and the requirements clause they validate.
- Properties tested:
  - Property 1 — Lifecycle transitions only along the Req 6.1 graph
  - Property 2 — No transitions out of terminal states
  - Property 3 — `grand_total = subtotal − discount + tax + shipping`
  - Property 4 — Pricing non-negativity (Req 3.5, 20.1)
  - Property 5 — No-op chain identity (Req 20.2)
  - Property 6 — Idempotent-chain stability (Req 20.3)
  - Property 7 — Cache `put` idempotence (Req 19.1)
  - Property 8 — Order JSON round-trip (Req 18.3)
  - Property 9 — Per-Order serialization under concurrency (Req 11.3, 11.4)
- Checkpoints (tasks 16, 22) split the plan into a domain-and-infrastructure phase and an orchestration-and-API phase so coverage and design issues surface before integration.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4"] },
    { "id": 3, "tasks": ["2.1", "2.2", "2.3", "2.4", "8.1", "8.2"] },
    { "id": 4, "tasks": ["2.5", "3.1", "4.1", "5.1", "5.2", "6.1", "7.1", "7.2"] },
    { "id": 5, "tasks": ["3.2", "4.2", "5.3", "6.2", "6.3"] },
    { "id": 6, "tasks": ["3.3", "4.3", "4.4", "5.4", "5.5", "6.4", "7.3", "9.1", "9.3", "11.1"] },
    { "id": 7, "tasks": ["6.5", "6.6", "6.7", "6.8", "6.9", "9.2", "10.1", "11.2", "11.3", "12.1", "13.1", "14.1", "15.1", "15.2"] },
    { "id": 8, "tasks": ["9.4", "10.2", "12.2", "12.3", "13.2", "13.3", "14.2", "15.3"] },
    { "id": 9, "tasks": ["10.3", "10.5", "14.3"] },
    { "id": 10, "tasks": ["10.4", "17.1", "17.2"] },
    { "id": 11, "tasks": ["17.3"] },
    { "id": 12, "tasks": ["17.4", "18.1"] },
    { "id": 13, "tasks": ["18.2", "18.3", "19.1", "19.2"] },
    { "id": 14, "tasks": ["18.4", "19.3", "20.1"] },
    { "id": 15, "tasks": ["20.2", "20.3", "21.1", "21.2", "21.3", "21.4"] }
  ]
}
```
