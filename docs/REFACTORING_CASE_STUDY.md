# Refactoring Case Study

## Subject: Monolithic Pricing and Notification Dispatch

This case study traces two closely related refactorings applied to the Dynamic Order
Processing Service. Both started from the same root problem — business logic that was
correct but tightly coupled — and both were resolved by introducing well-known GoF patterns.

---

## 1. Pricing: Before

Imagine the initial implementation of pricing as a single method inside a hypothetical
`OrderProcessor` class:

```java
// BEFORE — all pricing logic in one place
public Order price(Order order, String profile) {
    BigDecimal subtotal = order.getItems().stream()
        .map(i -> i.unitPrice().amount().multiply(BigDecimal.valueOf(i.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal discount = BigDecimal.ZERO;
    if ("PREMIUM".equals(profile)) {
        discount = subtotal.multiply(new BigDecimal("0.10"));
    } else if ("SALE".equals(profile)) {
        discount = subtotal.multiply(new BigDecimal("0.20"));
    }

    BigDecimal tax = subtotal.subtract(discount).multiply(new BigDecimal("0.08"));

    BigDecimal shipping;
    if (subtotal.compareTo(new BigDecimal("50")) >= 0) {
        shipping = BigDecimal.ZERO;          // free shipping over $50
    } else {
        shipping = new BigDecimal("5.99");
    }

    BigDecimal grandTotal = subtotal.subtract(discount).add(tax).add(shipping);
    return order.withTotals(
        Money.of(subtotal, USD), Money.of(discount, USD),
        Money.of(tax, USD), Money.of(shipping, USD), Money.of(grandTotal, USD)
    ).withStatus(PRICED);
}
```

**Problems with this design:**

- **OCP violation.** Adding a new discount type (e.g., a loyalty-points reduction) requires
  editing `OrderProcessor`. Every edit risks breaking existing pricing logic.
- **SRP violation.** One method owns discount logic, tax logic, and shipping logic. Each has
  a different reason to change (marketing changes discounts; finance changes tax rates;
  logistics changes shipping thresholds).
- **Untestability.** There is no way to test the tax calculation in isolation without also
  exercising discount and shipping code. Property-based tests that verify
  `grand_total = subtotal − discount + tax + shipping` must construct the entire method's
  context.
- **No extensibility seam.** A new pricing profile requires a new `else if` branch, which
  grows the method indefinitely.

---

## 2. Pricing: Refactoring Applied (Strategy + Factory Method)

The refactoring introduced the **Strategy** pattern for individual pricing algorithms and
the **Factory Method** pattern for chain construction.

```java
// AFTER — Strategy interface
public interface PricingStrategy {
    PriceContribution apply(Order order);
    boolean isIdempotent();
}

// Individual strategies — each has one reason to change
public class DiscountStrategy implements PricingStrategy { ... }
public class TaxStrategy       implements PricingStrategy { ... }
public class ShippingStrategy  implements PricingStrategy { ... }
public class NoOpStrategy      implements PricingStrategy { ... }

// Factory Method — builds the chain from a profile name
public class PricingStrategyChainFactory {
    public List<PricingStrategy> build(String profile) { ... }
}

// Engine — never edited when a new strategy is added
public class PricingEngine {
    public Order price(Order order, List<PricingStrategy> chain) {
        // iterate chain, accumulate PriceContribution, compute totals
    }
}
```

**Benefits:**

- **OCP.** Adding a `LoyaltyPointsStrategy` means adding one class and registering it in
  the factory for the relevant profile. `PricingEngine` is never touched.
- **SRP.** Each strategy class has exactly one reason to change. Tax rate changes affect
  only `TaxStrategy`; free-shipping thresholds affect only `ShippingStrategy`.
- **Testability.** Each strategy is tested in isolation. Property tests for
  `grand_total = subtotal − discount + tax + shipping` (Property 3) and non-negativity
  (Property 4) run against `PricingEngine` with any combination of strategies, including
  generated random chains.
- **LSP.** Every `PricingStrategy` implementation returns a `PriceContribution` with
  non-negative component amounts, so `PricingEngine` can substitute any implementation
  without weakening its postconditions.

---

## 3. Notification Dispatch: Before

The same `OrderProcessor` class originally dispatched notifications inline:

```java
// BEFORE — notification channels hard-coded
public void notify(Order order) {
    try {
        emailClient.send(order.getCustomerEmail(), buildEmailBody(order));
    } catch (Exception e) {
        log.error("Email failed", e);
    }
    try {
        smsGateway.send(order.getCustomerPhone(), buildSmsText(order));
    } catch (Exception e) {
        log.error("SMS failed", e);
    }
    // Adding a webhook channel requires editing this method
}
```

**Problems:**

- **OCP violation.** Every new channel (webhook, push notification, Slack) requires editing
  `OrderProcessor`.
- **SRP violation.** `OrderProcessor` owns order lifecycle logic *and* notification
  delivery logic.
- **Fragile fan-out.** The try/catch blocks are duplicated per channel. A missing catch
  block on a new channel could propagate an exception and abort delivery to subsequent
  channels.

---

## 4. Notification Dispatch: Refactoring Applied (Observer)

The refactoring introduced the **Observer** pattern, separating the subject
(`NotificationDispatcher`) from the concrete observers (channel implementations).

```java
// AFTER — Observer interface
public interface OrderEventListener {
    void onOrderEvent(OrderNotificationEvent event);
}

// Subject — never edited when a new channel is added
public class NotificationDispatcher {
    private final List<OrderEventListener> listeners;

    public void dispatch(OrderNotificationEvent event) {
        for (OrderEventListener listener : listeners) {
            try {
                listener.onOrderEvent(event);
            } catch (Exception e) {
                // record failure, continue to remaining listeners (Req 8.3)
                recordFailure(listener, event, e);
            }
        }
    }
}

// Concrete observers — each in its own class
public class EmailNotificationChannel   implements OrderEventListener { ... }
public class SmsNotificationChannel     implements OrderEventListener { ... }
public class WebhookNotificationChannel implements OrderEventListener { ... }
```

**Benefits:**

- **OCP.** A new channel is a new class registered as a Spring bean. `NotificationDispatcher`
  is never modified.
- **SRP.** `NotificationDispatcher` owns only fan-out and failure isolation. Each channel
  class owns only its own transport logic.
- **Resilience.** The single fan-out loop with a uniform try/catch guarantees that a failing
  channel never silences subsequent channels (Requirement 8.3), regardless of how many
  channels are registered.
- **Testability.** Unit tests stub `OrderEventListener` to verify fan-out count and failure
  isolation without standing up any real transport.

---

## Summary

| Concern | Before | After | Patterns applied |
|---------|--------|-------|-----------------|
| Pricing | All algorithms in one method; `else if` chains for profiles | One class per algorithm; factory builds the chain | Strategy, Factory Method |
| Notifications | Hard-coded channel calls in the processor | Registered observers; dispatcher fans out uniformly | Observer |
| OCP compliance | Editing existing classes for every extension | Adding new classes only | Both refactorings |
| Testability | Monolithic; no isolation | Each unit independently testable; property tests feasible | Both refactorings |
