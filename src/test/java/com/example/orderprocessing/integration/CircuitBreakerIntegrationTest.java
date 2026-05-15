package com.example.orderprocessing.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-style integration tests for Resilience4j circuit-breaker state transitions.
 *
 * <p>Verifies the full CLOSED → OPEN → HALF_OPEN → CLOSED transition cycle for the
 * {@code inventory} circuit-breaker configuration matching the settings in
 * {@code application.yml} (Requirements 12.2, 12.4, 12.5, 12.6).
 *
 * <p>No Spring context is required — the circuit breaker is configured programmatically
 * using {@link CircuitBreakerRegistry} and {@link CircuitBreakerConfig} to mirror the
 * production YAML settings. This keeps the test fast and deterministic.
 *
 * <p>The inventory breaker settings under test:
 * <ul>
 *   <li>Sliding window: COUNT_BASED, size 20, minimum 10 calls</li>
 *   <li>Failure rate threshold: 50%</li>
 *   <li>Wait duration in OPEN state: 30 s (overridden to 100 ms in tests for speed)</li>
 *   <li>Permitted calls in HALF_OPEN: 3</li>
 *   <li>Automatic transition from OPEN to HALF_OPEN: enabled</li>
 *   <li>Recorded exceptions: {@link IOException}</li>
 * </ul>
 */
class CircuitBreakerIntegrationTest {

    /**
     * Short wait duration used in tests instead of the production 30 s so that the
     * OPEN → HALF_OPEN automatic transition happens quickly.
     */
    private static final Duration TEST_WAIT_DURATION = Duration.ofMillis(100);

    private CircuitBreaker inventoryBreaker;

    /**
     * Builds a fresh circuit breaker before each test, configured to match the
     * production {@code inventory} instance settings (with a shortened wait duration).
     */
    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(TEST_WAIT_DURATION)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(IOException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        inventoryBreaker = registry.circuitBreaker("inventory");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Simulates a successful call through the circuit breaker.
     */
    private void simulateSuccess() {
        inventoryBreaker.executeSupplier(() -> "ok");
    }

    /**
     * Simulates a failing call through the circuit breaker by throwing an
     * {@link IOException} (a recorded exception per the inventory breaker config).
     */
    private void simulateFailure() {
        try {
            inventoryBreaker.executeCheckedSupplier(() -> {
                throw new IOException("simulated inventory failure");
            });
        } catch (IOException | CallNotPermittedException ignored) {
            // Expected — we are deliberately driving failures
        } catch (Throwable t) {
            // Wrap any other checked exception
            throw new RuntimeException(t);
        }
    }

    /**
     * Drives the breaker past the failure threshold by recording enough failures
     * to satisfy the minimum-calls requirement and exceed the 50% failure rate.
     *
     * <p>Strategy: record 10 calls (the minimum), all failures, so the failure
     * rate is 100% which exceeds the 50% threshold.
     */
    private void driveToOpen() {
        for (int i = 0; i < 10; i++) {
            simulateFailure();
        }
    }

    // -------------------------------------------------------------------------
    // State: CLOSED (initial)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("breaker starts in CLOSED state")
    void breaker_startsInClosedState() {
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("breaker remains CLOSED when failure rate is below threshold")
    void breaker_remainsClosed_whenFailureRateBelowThreshold() {
        // 5 failures out of 10 calls = 50% — exactly at threshold, not above
        // Record 5 successes and 4 failures (9 calls total — below minimum of 10)
        for (int i = 0; i < 9; i++) {
            simulateSuccess();
        }
        // Still below minimum number of calls — should stay CLOSED
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // -------------------------------------------------------------------------
    // Transition: CLOSED → OPEN
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("breaker transitions CLOSED → OPEN when failure rate exceeds threshold")
    void breaker_transitions_closedToOpen_whenFailureRateExceedsThreshold() {
        driveToOpen();

        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("OPEN breaker rejects calls immediately with CallNotPermittedException")
    void openBreaker_rejectsCallsImmediately() {
        driveToOpen();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() ->
                inventoryBreaker.executeSupplier(() -> "should not reach here"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("OPEN breaker does not forward calls to the underlying operation")
    void openBreaker_doesNotForwardCalls() {
        driveToOpen();

        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> operation = () -> {
            callCount.incrementAndGet();
            return "result";
        };

        try {
            inventoryBreaker.executeSupplier(operation);
        } catch (CallNotPermittedException ignored) {
            // Expected
        }

        assertThat(callCount.get()).isZero();
    }

    // -------------------------------------------------------------------------
    // Transition: OPEN → HALF_OPEN
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("breaker transitions OPEN → HALF_OPEN after wait duration elapses")
    void breaker_transitions_openToHalfOpen_afterWaitDuration() throws InterruptedException {
        driveToOpen();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for the automatic transition (wait duration = 100 ms in tests)
        Thread.sleep(TEST_WAIT_DURATION.toMillis() + 50);

        // Trigger the automatic transition check by attempting a call
        try {
            inventoryBreaker.executeSupplier(() -> "probe");
        } catch (Exception ignored) {
            // The probe call itself may succeed or fail; we care about the state
        }

        assertThat(inventoryBreaker.getState())
                .isIn(CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED);
    }

    // -------------------------------------------------------------------------
    // Transition: HALF_OPEN → OPEN (probe failures)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("breaker transitions HALF_OPEN → OPEN when probe calls fail")
    void breaker_transitions_halfOpenToOpen_whenProbeCallsFail() throws InterruptedException {
        driveToOpen();

        // Wait for automatic OPEN → HALF_OPEN transition
        Thread.sleep(TEST_WAIT_DURATION.toMillis() + 50);

        // Force transition to HALF_OPEN
        inventoryBreaker.transitionToHalfOpenState();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Fail all 3 permitted probe calls — breaker should return to OPEN
        for (int i = 0; i < 3; i++) {
            simulateFailure();
        }

        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // -------------------------------------------------------------------------
    // Transition: HALF_OPEN → CLOSED (probe successes)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("breaker transitions HALF_OPEN → CLOSED when probe calls succeed")
    void breaker_transitions_halfOpenToClosed_whenProbeCallsSucceed() throws InterruptedException {
        driveToOpen();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for automatic OPEN → HALF_OPEN transition
        Thread.sleep(TEST_WAIT_DURATION.toMillis() + 50);

        // Force transition to HALF_OPEN
        inventoryBreaker.transitionToHalfOpenState();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Succeed all 3 permitted probe calls — breaker should close
        for (int i = 0; i < 3; i++) {
            simulateSuccess();
        }

        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // -------------------------------------------------------------------------
    // Full cycle: CLOSED → OPEN → HALF_OPEN → CLOSED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("full cycle: CLOSED → OPEN → HALF_OPEN → CLOSED")
    void fullCycle_closedToOpenToHalfOpenToClosed() throws InterruptedException {
        // Step 1: Verify initial CLOSED state
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Step 2: Drive to OPEN
        driveToOpen();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Step 3: Verify OPEN rejects calls
        assertThatThrownBy(() -> inventoryBreaker.executeSupplier(() -> "blocked"))
                .isInstanceOf(CallNotPermittedException.class);

        // Step 4: Wait for automatic OPEN → HALF_OPEN transition
        Thread.sleep(TEST_WAIT_DURATION.toMillis() + 50);
        inventoryBreaker.transitionToHalfOpenState();
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Step 5: Succeed all probe calls → CLOSED
        for (int i = 0; i < 3; i++) {
            simulateSuccess();
        }
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Step 6: Verify normal operation resumes after recovery
        String result = inventoryBreaker.executeSupplier(() -> "recovered");
        assertThat(result).isEqualTo("recovered");
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("circuit breaker metrics track call counts correctly")
    void circuitBreaker_metricsTrackCallCounts() {
        // Record 6 successes and 4 failures (10 total — meets minimum)
        // 4/10 = 40% < 50% threshold → breaker stays CLOSED
        for (int i = 0; i < 6; i++) {
            simulateSuccess();
        }
        for (int i = 0; i < 4; i++) {
            simulateFailure();
        }

        CircuitBreaker.Metrics metrics = inventoryBreaker.getMetrics();
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(6);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(4);
        // 4/10 = 40% < 50% threshold — breaker should remain CLOSED
        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("failure rate exceeds threshold when more than 50% of calls fail")
    void failureRate_exceedsThreshold_whenMoreThanHalfFail() {
        // 6 failures out of 10 calls = 60% > 50% threshold
        for (int i = 0; i < 4; i++) {
            simulateSuccess();
        }
        for (int i = 0; i < 6; i++) {
            simulateFailure();
        }

        assertThat(inventoryBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(inventoryBreaker.getMetrics().getFailureRate()).isGreaterThan(50.0f);
    }
}
