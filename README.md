# Dynamic Order Processing Service

A standalone Spring Boot 3.2+ backend service built on Java 21 that accepts customer orders,
validates them, computes dynamic pricing (discounts, taxes, shipping), reserves inventory,
authorises payment, and drives each order through a deterministic lifecycle — all exposed via
a REST API.

The service is engineered around three pillars:

- **Maintainability** — hexagonal (ports-and-adapters) architecture, deliberate GoF pattern
  usage (Builder, Factory Method, Adapter, Decorator, Facade, Strategy, Observer, Chain of
  Responsibility, State, Command, Template Method), and SOLID principles enforced by
  domain-owned port interfaces.
- **Scalability** — Redis cache-aside layer for `GET /orders/{id}` and Java 21 virtual threads
  for both Tomcat request handling and the internal pipeline executor.
- **Robustness** — Resilience4j circuit breakers around every external dependency, fail-fast
  HTTP 503 responses, and an `OrderStatusEvent` audit log of every accepted state transition.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 21+ | See environment setup below |
| Maven | 3.9+ | See environment setup below |
| PostgreSQL | 15+ | Running on `localhost:5432`, database `orderprocessing` |
| Redis | 7+ | Running on `localhost:6379` |

---

## Build

```bash
# Full quality gate: compile + unit tests + integration tests + JaCoCo coverage check
mvn clean verify
```

The build fails if line coverage drops below **90 %** or branch coverage below **80 %**
(enforced by `jacoco-maven-plugin`).

### Environment setup

#### macOS

```bash
# Install JDK 21 and Maven via Homebrew
brew install openjdk@21 maven

export JAVA_HOME=$(brew --prefix openjdk@21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Or use [SDKMAN](https://sdkman.io/):

```bash
sdk install java 21.0.3-tem
sdk install maven 3.9.6
```

#### Linux (Ubuntu / Debian)

```bash
sudo apt update && sudo apt install openjdk-21-jdk maven

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

> Add the `export` lines to `~/.zshrc` (macOS) or `~/.bashrc` (Linux) to make them permanent.

#### Windows (PowerShell)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH      = "$env:JAVA_HOME\bin;C:\path\to\apache-maven\bin;$env:PATH"
```

---

## Run

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080` by default.

---

## Demo

Run a self-contained demo that exercises the main features without needing PostgreSQL, Redis, or any external services:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

The demo walks through four scenarios and prints results to the console:

1. **Happy path** — full lifecycle: CREATED → VALIDATED → PRICED → RESERVED → CONFIRMED, with pricing breakdown and audit log
2. **Idempotency** — submitting the same order twice with the same `Idempotency-Key` returns the same Order_ID
3. **Validation failure** — how a FAILED order looks with a failure reason
4. **Cancellation** — cancel a CONFIRMED order; inventory release is triggered

All infrastructure (database, cache, inventory, payment) is replaced with in-memory stubs for the demo profile.

---

## Configuration

All keys live in `src/main/resources/application.yml`. Override any key via environment
variable or a profile-specific `application-{profile}.yml`.

### Datasource

| Key | Default | Description |
|-----|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/orderprocessing` | JDBC URL for the primary relational store |
| `spring.datasource.username` | `orderprocessing_user` | Database username |
| `spring.datasource.password` | `changeme` | Database password — **change before deployment** |

### Redis

| Key | Default | Description |
|-----|---------|-------------|
| `spring.data.redis.host` | `localhost` | Redis hostname |
| `spring.data.redis.port` | `6379` | Redis port |
| `app.cache.order.ttl` | `5m` | TTL applied to every cached `Order` entry (ISO-8601 duration) |

### Resilience4j Circuit Breakers

| Instance | Key prefix | Notable defaults |
|----------|-----------|-----------------|
| `inventory` | `resilience4j.circuitbreaker.instances.inventory` | failure threshold 50 %, open wait 30 s |
| `payment` | `resilience4j.circuitbreaker.instances.payment` | failure threshold 40 %, open wait 60 s |
| `cache` | `resilience4j.circuitbreaker.instances.cache` | failure threshold 50 %, open wait 15 s |

Common sub-keys for each instance:

| Sub-key | Description |
|---------|-------------|
| `slidingWindowSize` | Number of calls in the rolling window |
| `minimumNumberOfCalls` | Minimum calls before the rate is evaluated |
| `failureRateThreshold` | Percentage of failures that opens the breaker |
| `waitDurationInOpenState` | How long the breaker stays OPEN before moving to HALF_OPEN |
| `permittedNumberOfCallsInHalfOpenState` | Trial calls allowed in HALF_OPEN |

### External Providers

| Key | Default | Description |
|-----|---------|-------------|
| `app.inventory.base-url` | `http://localhost:9001` | Base URL of the mock inventory provider |
| `app.payment.base-url` | `http://localhost:9002` | Base URL of the mock payment provider |

### Virtual Threads

| Key | Default | Description |
|-----|---------|-------------|
| `spring.threads.virtual.enabled` | `true` | Enables Java 21 virtual threads for Tomcat |

---

## API Summary

All endpoints accept and return `application/json`. Errors share the envelope:

```json
{
  "code": "STABLE_ERROR_CODE",
  "message": "Human-readable description",
  "correlationId": "uuid",
  "details": {}
}
```

| Method | Path | Description | Success | Notable error codes |
|--------|------|-------------|---------|---------------------|
| `POST` | `/api/v1/orders` | Create a new order. Accepts optional `Idempotency-Key` header for deduplication. Returns `Location` header pointing to the created resource. | `201 Created` | `400 VALIDATION_FAILED`, `409 IDEMPOTENCY_CONFLICT`, `503 DEPENDENCY_UNAVAILABLE` |
| `GET` | `/api/v1/orders/{id}` | Retrieve a single order by its Order_ID. Served from Redis cache on hit; falls back to the primary store on miss. | `200 OK` | `404 ORDER_NOT_FOUND` |
| `GET` | `/api/v1/orders` | List orders filtered by `status`, `customerId`, `from`, `to` with `page`/`size` pagination. | `200 OK` | `400 INVALID_QUERY` |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancel an order that has not yet shipped. Body: `{ "reason": "..." }`. Releases reserved inventory when applicable. | `200 OK` | `404 ORDER_NOT_FOUND`, `409 INVALID_TRANSITION`, `503 DEPENDENCY_UNAVAILABLE` |
| `GET` | `/api/v1/orders/{id}/events` | Retrieve the full audit log of `OrderStatusEvent` records for an order. | `200 OK` | `404 ORDER_NOT_FOUND` |

### Order lifecycle

```
CREATED → VALIDATED → PRICED → RESERVED → CONFIRMED → SHIPPED → DELIVERED
                                                      ↘ CANCELLED
Any non-terminal state → CANCELLED  (explicit cancel command)
Any non-terminal state → FAILED     (validation / pricing / dependency error)
```

---

## Documentation

| Artifact | Location | Description |
|----------|----------|-------------|
| Pattern Inventory | [`docs/PATTERNS.md`](docs/PATTERNS.md) | Every GoF pattern used, implementing classes, and rationale |
| Refactoring Case Study | [`docs/REFACTORING_CASE_STUDY.md`](docs/REFACTORING_CASE_STUDY.md) | Before/after analysis of a tightly-coupled section |
| Architecture Diagram | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Mermaid component diagram with layer descriptions |

---

## Observability

Actuator endpoints are exposed at `/actuator`:

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Liveness + readiness (includes Redis and external dependency checks) |
| `/actuator/metrics` | Micrometer metrics (request count/latency, circuit-breaker state, cache hit/miss) |
| `/actuator/prometheus` | Prometheus-format scrape endpoint |
