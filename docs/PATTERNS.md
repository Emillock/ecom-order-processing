# Pattern Inventory

This document enumerates every Gang-of-Four (GoF) design pattern applied in the Dynamic Order
Processing Service. For each pattern it lists the category, the implementing fully-qualified
class names (relative to `com.example.orderprocessing`), and the architectural rationale.

The inventory satisfies Requirements 13.1 (≥ 2 Creational), 13.2 (≥ 2 Structural), and
13.3 (≥ 2 Behavioral), and explicitly covers the mandatory patterns called out in
Requirements 13.4 (Strategy), 13.5 (Adapter), and 13.6 (Observer).

---

## Creational Patterns

### Builder

| Attribute | Value |
|-----------|-------|
| **Category** | Creational |
| **Pattern** | Builder |
| **Implementing classes** | `domain.model.OrderBuilder` → produces `domain.model.Order` |
| **Requirement** | 13.1, 1.1 |

**Rationale.** `Order` is an immutable aggregate root with many fields (id, items, status,
five monetary totals, idempotency key, timestamps, failure reason). A telescoping constructor
would be unreadable and error-prone. `OrderBuilder` enforces required fields at compile time,
applies sensible defaults (e.g., `status = CREATED`, `createdAt = Instant.now()`), and
prevents partially-constructed `Order` instances from escaping the builder via a `build()`
guard that throws `IllegalStateException` on missing required fields. Because `Order`'s
constructor is package-private, the builder is the only legal construction path.

---

### Factory Method

| Attribute | Value |
|-----------|-------|
| **Category** | Creational |
| **Pattern** | Factory Method |
| **Implementing classes** | `domain.pricing.PricingStrategyChainFactory` |
| **Requirement** | 13.1, 3.1, 3.2 |

**Rationale.** The set of active `PricingStrategy` implementations and their ordering is
configuration-driven (keyed by a *pricing profile* name such as `"default"`). Embedding
`new DiscountStrategy()`, `new TaxStrategy()`, … directly in `PricingEngine` would violate
OCP — adding a strategy would require editing the engine. `PricingStrategyChainFactory`
encapsulates the construction decision: callers receive an ordered `List<PricingStrategy>`
without knowing which concrete types are in it. An unknown profile causes the factory to
throw, which the pipeline catches and converts to a `FAILED` transition with a
`pricing-error` reason (Requirement 3.6).

---

## Structural Patterns

### Adapter

| Attribute | Value |
|-----------|-------|
| **Category** | Structural |
| **Pattern** | Adapter |
| **Implementing classes** | `infrastructure.inventory.HttpInventoryAdapter` (implements `domain.port.InventoryPort`), `infrastructure.payment.HttpPaymentAdapter` (implements `domain.port.PaymentPort`) |
| **Requirement** | 13.2, 13.5, 4, 5 |

**Rationale.** The domain pipeline depends on the narrow ports `InventoryPort` and
`PaymentPort` — pure Java interfaces with no HTTP, JSON, or Spring types. The external
(mock) providers speak HTTP/JSON with their own request/response shapes. The adapter classes
translate between the two worlds: they accept domain types (`OrderId`, `List<OrderItem>`,
`Money`), call the provider via `RestClient`, and map the HTTP response to domain result
types (`ReservationResult`, `AuthorizationResult`). This means the domain is completely
insulated from provider-specific changes, and the adapters can be swapped for real providers
without touching any business logic.

---

### Decorator

| Attribute | Value |
|-----------|-------|
| **Category** | Structural |
| **Pattern** | Decorator |
| **Implementing classes** | `infrastructure.cache.CachingOrderRepository` decorates `infrastructure.persistence.JpaOrderRepository`; both implement `domain.port.OrderRepository` |
| **Requirement** | 13.2, 9.3–9.5 |

**Rationale.** The cache-aside behaviour (read-through on miss, populate before return,
evict on write, bypass when `isAvailable()` is false) is a cross-cutting concern that should
not live inside the JPA repository. `CachingOrderRepository` wraps `JpaOrderRepository`
transparently: callers depend only on `OrderRepository`, so the cache layer can be added,
removed, or replaced without touching `OrderService` or any test that mocks the port. This
is a textbook OCP application — the JPA repository is never modified to accommodate caching.

---

### Facade

| Attribute | Value |
|-----------|-------|
| **Category** | Structural |
| **Pattern** | Facade |
| **Implementing classes** | `application.OrderService` |
| **Requirement** | 13.2 |

**Rationale.** `OrderController` should not need to know about the pipeline, the lock
registry, the idempotency store, the command objects, or the repository. `OrderService`
provides a single, cohesive entry point (`create`, `get`, `list`, `cancel`, `events`) that
hides all of that complexity. This keeps the controller thin (HTTP binding only) and makes
the application layer independently testable by mocking just the five service methods.

---

## Behavioral Patterns

### Strategy

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | Strategy |
| **Implementing classes** | `domain.pricing.PricingStrategy` (interface); `domain.pricing.strategies.DiscountStrategy`, `domain.pricing.strategies.TaxStrategy`, `domain.pricing.strategies.ShippingStrategy`, `domain.pricing.strategies.NoOpStrategy` |
| **Requirement** | 13.3, 13.4, 3.2, 14.2 |

**Rationale.** Pricing algorithms vary by business context (promotional discounts, regional
tax rules, carrier-based shipping costs) and must be composable. The Strategy pattern lets
`PricingEngine` iterate over a `List<PricingStrategy>` without knowing the concrete types.
Adding a new pricing rule — say, a loyalty-points discount — means adding one class that
implements `PricingStrategy`; `PricingEngine` is never edited (OCP, Requirement 14.2).
Each strategy declares `isIdempotent()` so the property tests can verify that applying an
idempotent chain twice yields the same grand total (Requirement 20.3).

---

### Observer

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | Observer |
| **Implementing classes** | `domain.notification.NotificationDispatcher` (Subject); `domain.notification.OrderEventListener` (Observer interface); `infrastructure.notification.EmailNotificationChannel`, `infrastructure.notification.SmsNotificationChannel`, `infrastructure.notification.WebhookNotificationChannel` (concrete observers) |
| **Requirement** | 13.3, 13.6, 8, 14.2 |

**Rationale.** Notification channels are a classic open/closed extension point: the business
will add new channels (push notifications, Slack, etc.) without the dispatcher ever knowing
about them. `NotificationDispatcher` holds a list of registered `OrderEventListener`
instances and fans out every status-transition event to all of them. If one channel throws,
the dispatcher records the failure and continues to the remaining channels (Requirement 8.3),
so a broken email gateway cannot silence SMS or webhook delivery.

---

### Chain of Responsibility

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | Chain of Responsibility |
| **Implementing classes** | `domain.validation.ValidationChain`; `domain.validation.ValidationRule` (interface); `domain.validation.rules.NonEmptyItemsRule`, `domain.validation.rules.PositiveQuantityRule`, `domain.validation.rules.KnownSkuRule`, `domain.validation.rules.IdempotencyRule` |
| **Requirement** | 13.3, 2, 14.2 |

**Rationale.** Validation rules are independent checks that must all pass before an order
advances. Encoding them as a chain means each rule is a self-contained class with a single
responsibility, and new rules are added by registering a new `ValidationRule` bean — the
chain orchestrator is never modified (OCP, Requirement 2.4). The chain aggregates the first
failure's rule identifier and message onto the order before transitioning it to `FAILED`
(Requirement 2.3).

---

### State

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | State |
| **Implementing classes** | `domain.lifecycle.OrderState` (interface); `domain.lifecycle.states.CreatedState`, `domain.lifecycle.states.ValidatedState`, `domain.lifecycle.states.PricedState`, `domain.lifecycle.states.ReservedState`, `domain.lifecycle.states.ConfirmedState`, `domain.lifecycle.states.ShippedState`, `domain.lifecycle.states.DeliveredState`, `domain.lifecycle.states.CancelledState`, `domain.lifecycle.states.FailedState`; orchestrated by `domain.lifecycle.OrderStateMachine` |
| **Requirement** | 13.3, 6 |

**Rationale.** The order lifecycle has nine states and a non-trivial transition graph
(Requirement 6.1). Encoding all transition logic in a single `switch` statement would
produce a fragile, hard-to-test class. Each `OrderState` implementation declares its own
allowed outgoing transitions and whether it is terminal. `OrderStateMachine.assertTransition`
delegates to the current state, so adding a new state means adding a class — the machine
itself is not edited. Terminal states (`DeliveredState`, `CancelledState`, `FailedState`)
declare an empty allowed-transitions set, which is how Requirement 6.4 is enforced.

---

### Command

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | Command |
| **Implementing classes** | `application.command.OrderCommand` (interface); `application.command.CreateOrderCommand`, `application.command.CancelOrderCommand`, `application.command.TransitionOrderCommand` |
| **Requirement** | 13.3, 6.3, 7 |

**Rationale.** Every state-mutating lifecycle action must be serialised behind the per-order
lock and must produce an `OrderStatusEvent` audit record. Encapsulating each action as a
`Command` object makes the lock-acquire/execute/audit pattern uniform and keeps
`OrderService` free of duplicated locking boilerplate. Commands also provide a natural
extension point: a future undo/redo or command-queue feature can be added without changing
the service facade.

---

### Template Method

| Attribute | Value |
|-----------|-------|
| **Category** | Behavioral |
| **Pattern** | Template Method |
| **Implementing classes** | `application.OrderProcessingPipeline` |
| **Requirement** | 13.3 |

**Rationale.** The processing pipeline always executes the same five stages in the same
order: validate → price → reserve → pay → notify. The *shape* of the pipeline is invariant;
only the *implementation* of each stage varies (different validation rules, pricing
strategies, adapters). `OrderProcessingPipeline` defines the fixed `run(Order)` template
method and declares each stage as an abstract (or overridable) method. This prevents
accidental reordering of stages and makes the pipeline independently testable by overriding
individual stage methods in test subclasses.
