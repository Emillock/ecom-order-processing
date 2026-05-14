# Engineering Standards

These standards apply to all code in this project.

## Build & Tooling

- **Language:** Java 21 (use language features like records, sealed types, pattern matching, and virtual threads where appropriate).
- **Framework:** Spring Boot.
- **Build tool:** Maven. The project must be buildable with `mvn clean verify` from the repository root.
- **Project layout:** Standard Maven layout (`src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`).

## SOLID Principles

All production code must demonstrably follow SOLID:

- **Single Responsibility (SRP):** Every class has one documented reason to change. Split classes that mix concerns (e.g., HTTP handling + business logic + persistence).
- **Open/Closed (OCP):** Behavior is extended by adding new types, not by modifying existing ones. Pricing strategies, validation rules, and notification channels must be addable without editing their consumers.
- **Liskov Substitution (LSP):** Subtypes must be usable wherever their supertype is, without strengthening preconditions or weakening postconditions.
- **Interface Segregation (ISP):** Prefer multiple narrow interfaces over a single wide one. Clients depend only on methods they use.
- **Dependency Inversion (DIP):** High-level modules depend on abstractions. Inject collaborators via constructor injection; never `new` a service-layer dependency inside business logic.

## Design Patterns (GoF)

Apply patterns deliberately to solve real coupling or extensibility problems. Avoid pattern-for-pattern's-sake.

- **Creational:** Use Factory Method, Abstract Factory, Builder, or Prototype where construction is complex or varies by context.
- **Structural:** Use Adapter (mandatory for external/mock providers), Decorator, Facade, or Proxy where coupling needs to be inverted or behavior layered.
- **Behavioral:** Use Strategy (mandatory for pricing), Observer (mandatory for notifications), Command, Template Method, or Chain of Responsibility (e.g., for validation rules) where algorithms or workflows must be pluggable.

Every applied pattern must be listed in the repository's Pattern Inventory with the implementing classes and the rationale.

## Testing

- **Framework:** JUnit 5 with Spring Boot Test. Use Mockito for collaborator stubbing and Testcontainers (or `@DataRedisTest` with an embedded/mock Redis) for integration where relevant.
- **Property-based testing:** Use jqwik (or equivalent) for correctness properties declared in the spec (pricing invariants, lifecycle invariants, serialization round-trip, cache idempotence).
- **Test types required:**
  - Unit tests for every service, strategy, adapter, validator, and domain rule.
  - Slice tests for controllers (`@WebMvcTest`) and persistence (`@DataJpaTest` / `@DataRedisTest`).
  - Integration tests covering the full order lifecycle and circuit-breaker behavior.
- **Coverage:**
  - **Minimum line coverage: 90%** across `src/main/java`, measured by JaCoCo.
  - **Minimum branch coverage: 80%** across `src/main/java`.
  - The Maven build must fail when coverage falls below these thresholds. Configure `jacoco-maven-plugin` with a `check` execution bound to the `verify` phase.
- **Coverage exclusions** are limited to: generated code, Spring Boot `@SpringBootApplication` main class, configuration-only classes with no branching logic, and DTOs/records without behavior. Exclusions must be explicit and justified in the `pom.xml`.

## Quality Gates

- The Maven build (`mvn clean verify`) must run: compile, unit tests, integration tests, JaCoCo coverage check, and static analysis if configured.
- All public classes and public methods must have a one-line Javadoc describing their single responsibility.
- No `TODO`/`FIXME` comments may be merged without an associated tracked task.
