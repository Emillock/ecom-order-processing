package com.example.orderprocessing.unit.infrastructure.inventory;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.ReservationResult;
import com.example.orderprocessing.infrastructure.inventory.HttpInventoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link HttpInventoryAdapter} HTTP translation logic.
 *
 * <p>Uses {@link MockRestServiceServer} backed by a {@link RestTemplate} to intercept
 * outbound HTTP calls without starting a real server. The adapter is constructed via
 * its package-private {@code RestClient} constructor so the mock server can be wired in.
 *
 * <p>Covers:
 * <ul>
 *   <li>200 OK → {@link ReservationResult#success()}</li>
 *   <li>409 Conflict with body → {@link ReservationResult#outOfStock(List)}</li>
 *   <li>409 Conflict with empty body → {@link ReservationResult#failed(String)}</li>
 *   <li>{@link org.springframework.web.client.RestClientException} → {@link ReservationResult#failed(String)}</li>
 *   <li>release() 200 OK → completes silently</li>
 *   <li>release() transport failure → logs warning, does not throw</li>
 *   <li>reserveFallback() → returns {@code dependency_unavailable:inventory}</li>
 *   <li>releaseFallback() → returns silently</li>
 * </ul>
 */
class HttpInventoryAdapterTest {

    private MockRestServiceServer mockServer;
    private HttpInventoryAdapter adapter;

    /** Shared test fixtures. */
    private OrderId orderId;
    private List<OrderItem> items;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        RestClient restClient = RestClient.create(restTemplate);
        adapter = new HttpInventoryAdapter(restClient);

        orderId = new OrderId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        items = List.of(
                new OrderItem(new Sku("SKU-A"), 2,
                        new Money(new BigDecimal("10.00"), Currency.getInstance("USD"))),
                new OrderItem(new Sku("SKU-B"), 1,
                        new Money(new BigDecimal("5.00"), Currency.getInstance("USD")))
        );
    }

    // -------------------------------------------------------------------------
    // reserve() — success path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve: 200 OK → ReservationResult.success()")
    void reserve_200Ok_returnsSuccess() {
        mockServer.expect(requestTo("/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":\"RESERVED\"}",
                        MediaType.APPLICATION_JSON));

        ReservationResult result = adapter.reserve(orderId, items);

        mockServer.verify();
        assertThat(result).isInstanceOf(ReservationResult.Success.class);
        assertThat(result.isSuccess()).isTrue();
    }

    // -------------------------------------------------------------------------
    // reserve() — out-of-stock path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve: 409 Conflict with unavailableSkus body → ReservationResult.outOfStock()")
    void reserve_409WithSkus_returnsOutOfStock() {
        String body = "{\"unavailableSkus\":[\"SKU-A\",\"SKU-B\"]}";
        mockServer.expect(requestTo("/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        ReservationResult result = adapter.reserve(orderId, items);

        mockServer.verify();
        assertThat(result).isInstanceOf(ReservationResult.OutOfStock.class);
        assertThat(result.getUnavailableSkus())
                .extracting(Sku::value)
                .containsExactlyInAnyOrder("SKU-A", "SKU-B");
    }

    @Test
    @DisplayName("reserve: 409 Conflict with empty body → ReservationResult.failed()")
    void reserve_409EmptyBody_returnsFailed() {
        mockServer.expect(requestTo("/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        ReservationResult result = adapter.reserve(orderId, items);

        mockServer.verify();
        assertThat(result).isInstanceOf(ReservationResult.Failed.class);
        assertThat(result.getReason()).contains("Out-of-stock but no SKUs returned");
    }

    // -------------------------------------------------------------------------
    // reserve() — transport failure path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve: RestClientException (connection refused) → ReservationResult.failed()")
    void reserve_transportFailure_returnsFailed() {
        mockServer.expect(requestTo("/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.io.IOException("Connection refused")));

        ReservationResult result = adapter.reserve(orderId, items);

        mockServer.verify();
        assertThat(result).isInstanceOf(ReservationResult.Failed.class);
        assertThat(result.getReason()).contains("Transport error");
    }

    @Test
    @DisplayName("reserve: 500 Internal Server Error → ReservationResult.failed()")
    void reserve_500ServerError_returnsFailed() {
        mockServer.expect(requestTo("/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ReservationResult result = adapter.reserve(orderId, items);

        mockServer.verify();
        assertThat(result).isInstanceOf(ReservationResult.Failed.class);
        assertThat(result.getReason()).contains("Transport error");
    }

    // -------------------------------------------------------------------------
    // release() — success path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("release: 200 OK → completes without throwing")
    void release_200Ok_completesNormally() {
        mockServer.expect(requestTo("/reservations/" + orderId.value()))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        // Must not throw
        adapter.release(orderId, items);

        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // release() — transport failure path (best-effort, must not throw)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("release: transport failure → logs warning, does not throw")
    void release_transportFailure_doesNotThrow() {
        mockServer.expect(requestTo("/reservations/" + orderId.value()))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withException(new java.io.IOException("Connection refused")));

        // release is best-effort — must swallow the exception
        adapter.release(orderId, items);

        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Fallback methods (circuit-breaker open path)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserveFallback: returns dependency_unavailable:inventory")
    void reserveFallback_returnsDependencyUnavailable() {
        Throwable cause = new RuntimeException("CircuitBreaker is OPEN");

        ReservationResult result = adapter.reserveFallback(orderId, items, cause);

        assertThat(result).isInstanceOf(ReservationResult.Failed.class);
        assertThat(result.getReason()).isEqualTo("dependency_unavailable:inventory");
    }

    @Test
    @DisplayName("releaseFallback: returns silently without throwing")
    void releaseFallback_doesNotThrow() {
        Throwable cause = new RuntimeException("CircuitBreaker is OPEN");

        // Must not throw
        adapter.releaseFallback(orderId, items, cause);
    }
}
