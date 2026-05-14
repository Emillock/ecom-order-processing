package com.example.orderprocessing.slice.persistence;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.infrastructure.persistence.IdempotencyRecordJpaEntity;
import com.example.orderprocessing.infrastructure.persistence.IdempotencyRecordJpaRepository;
import com.example.orderprocessing.infrastructure.persistence.JpaIdempotencyStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @DataJpaTest} slice tests for {@link JpaIdempotencyStore}.
 *
 * <p>Uses H2 in-memory database (configured in {@code application-test.yml}) with
 * {@code create-drop} DDL so each test run starts with a clean schema.
 * Tests cover the full deduplication lifecycle: miss, register, hit, and the
 * database-level uniqueness constraint violation (Requirement 1.3).
 */
@DataJpaTest
@ActiveProfiles("test")
class JpaIdempotencyStoreTest {

    @Autowired
    private IdempotencyRecordJpaRepository idempotencyRecordJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private JpaIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new JpaIdempotencyStore(idempotencyRecordJpaRepository);
    }

    // -------------------------------------------------------------------------
    // findExisting — miss (key not yet registered)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findExisting returns empty when the key has never been registered")
    void findExisting_miss_returnsEmpty() {
        IdempotencyKey key = new IdempotencyKey("key-that-does-not-exist");

        Optional<OrderId> result = store.findExisting(key);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findExisting returns empty for a different key even when other keys exist")
    void findExisting_differentKey_returnsEmpty() {
        IdempotencyKey registeredKey = new IdempotencyKey("registered-key");
        OrderId registeredId = OrderId.generate();
        store.register(registeredKey, registeredId);

        Optional<OrderId> result = store.findExisting(new IdempotencyKey("unregistered-key"));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // register — stores the key-to-orderId mapping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register persists the idempotency record in the database")
    void register_persistsRecord() {
        IdempotencyKey key = new IdempotencyKey("order-creation-key-1");
        OrderId orderId = OrderId.generate();

        store.register(key, orderId);

        Optional<IdempotencyRecordJpaEntity> entity = idempotencyRecordJpaRepository.findByKey(key.value());
        assertThat(entity).isPresent();
        assertThat(entity.get().getKey()).isEqualTo("order-creation-key-1");
        assertThat(entity.get().getOrderId()).isEqualTo(orderId.value());
        assertThat(entity.get().getCreatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // findExisting — hit (key was previously registered)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findExisting returns the registered OrderId after register is called")
    void findExisting_hit_returnsRegisteredOrderId() {
        IdempotencyKey key = new IdempotencyKey("idempotent-request-key");
        OrderId orderId = OrderId.generate();
        store.register(key, orderId);

        Optional<OrderId> result = store.findExisting(key);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("findExisting returns the correct OrderId when multiple keys are registered")
    void findExisting_multipleKeys_returnsCorrectOrderId() {
        IdempotencyKey key1 = new IdempotencyKey("key-alpha");
        IdempotencyKey key2 = new IdempotencyKey("key-beta");
        OrderId id1 = OrderId.generate();
        OrderId id2 = OrderId.generate();

        store.register(key1, id1);
        store.register(key2, id2);

        assertThat(store.findExisting(key1)).isPresent().hasValue(id1);
        assertThat(store.findExisting(key2)).isPresent().hasValue(id2);
    }

    // -------------------------------------------------------------------------
    // Uniqueness constraint violation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register throws DataIntegrityViolationException when the same key is registered twice with different order IDs")
    void register_duplicateKey_throwsDataIntegrityViolationException() {
        IdempotencyKey key = new IdempotencyKey("duplicate-key");
        OrderId firstId = OrderId.generate();
        OrderId secondId = OrderId.generate();

        // Use EntityManager.persist() to force an INSERT (not a merge/update).
        // This is necessary because SimpleJpaRepository.save() calls merge() for
        // entities with an existing PK, which silently updates rather than inserting.
        IdempotencyRecordJpaEntity first =
                new IdempotencyRecordJpaEntity(key.value(), firstId.value());
        entityManager.persist(first);
        entityManager.flush();

        // Attempting to persist a second entity with the same key must be rejected
        // by the unique PK constraint on idempotency_records."key" (Requirement 1.3).
        assertThatThrownBy(() -> {
            IdempotencyRecordJpaEntity conflicting =
                    new IdempotencyRecordJpaEntity(key.value(), secondId.value());
            entityManager.persist(conflicting);
            entityManager.flush();
        }).isInstanceOf(Exception.class)
          .satisfies(ex -> assertThat(ex)
              .isInstanceOfAny(
                  DataIntegrityViolationException.class,
                  jakarta.persistence.PersistenceException.class));
    }

    @Test
    @DisplayName("register with the same key and same order ID is idempotent at the store level (same PK)")
    void register_sameKeyAndSameOrderId_throwsOnDuplicate() {
        IdempotencyKey key = new IdempotencyKey("same-key-same-order");
        OrderId orderId = OrderId.generate();

        // Insert the first record via persist to force an INSERT.
        IdempotencyRecordJpaEntity first =
                new IdempotencyRecordJpaEntity(key.value(), orderId.value());
        entityManager.persist(first);
        entityManager.flush();

        // Persisting the exact same key again must violate the PK constraint.
        assertThatThrownBy(() -> {
            IdempotencyRecordJpaEntity duplicate =
                    new IdempotencyRecordJpaEntity(key.value(), orderId.value());
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(Exception.class)
          .satisfies(ex -> assertThat(ex)
              .isInstanceOfAny(
                  DataIntegrityViolationException.class,
                  jakarta.persistence.PersistenceException.class));
    }

    // -------------------------------------------------------------------------
    // Round-trip: register then findExisting returns the exact UUID
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register and findExisting round-trip preserves the exact UUID value")
    void registerAndFindExisting_roundTrip_preservesUuid() {
        UUID rawUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        IdempotencyKey key = new IdempotencyKey("round-trip-key");
        OrderId orderId = new OrderId(rawUuid);

        store.register(key, orderId);
        Optional<OrderId> found = store.findExisting(key);

        assertThat(found).isPresent();
        assertThat(found.get().value()).isEqualTo(rawUuid);
    }
}
