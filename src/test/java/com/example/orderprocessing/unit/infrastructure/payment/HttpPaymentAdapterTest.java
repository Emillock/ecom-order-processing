package com.example.orderprocessing.unit.infrastructure.payment;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.port.AuthorizationResult;
import com.example.orderprocessing.infrastructure.payment.HttpPaymentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link HttpPaymentAdapter} HTTP-to-domain translation logic.
 *
 * <p>Uses {@link MockRestServiceServer} to intercept outbound HTTP calls so no real
 * server is required. The adapter is constructed via its package-private
 * {@code RestClient}-accepting constructor, which was introduced for testability
 * (same approach as the inventory adapter in task 12.3).
 *
 * <p>Covers Requirements 5.1, 5.2, 5.3:
 * <ul>
 *   <li>Authorization success: {@code 200 + "AUTHORIZED"} → {@link AuthorizationResult.Authorized}</li>
 *   <li>Authorization decline: {@code 402} → {@link AuthorizationResult.Declined} with reason</li>
 *   <li>Transport failure: {@link org.springframework.web.client.RestClientException} →
 *       {@link AuthorizationResult.Failed}</li>
 * </ul>
 */
class HttpPaymentAdapterTest {

    private static final String BASE_URL = "http://payment-provider";
    private static final Currency USD = Currency.getInstance("USD");

    private MockRestServiceServer mockServer;
    private HttpPaymentAdapter adapter;

    /**
     * Sets up a {@link MockRestServiceServer} backed by a {@link RestTemplate} and wires
     * a {@link RestClient} built from that template into the adapter under test.
     */
    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        RestClient restClient = RestClient.builder(restTemplate)
                .baseUrl(BASE_URL)
                .build();
        adapter = new HttpPaymentAdapter(restClient);
    }

    // -------------------------------------------------------------------------
    // authorize() — success path
    // -------------------------------------------------------------------------

    /**
     * A {@code 200 OK} response with {@code "AUTHORIZED"} status must produce
     * {@link AuthorizationResult.Authorized} (Requirement 5.1).
     */
    @Test
    void authorize_200WithAuthorizedStatus_returnsAuthorized() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("99.99"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"status":"AUTHORIZED","declineReason":null}
                        """,
                        MediaType.APPLICATION_JSON));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Authorized.class, result,
                "200 + AUTHORIZED should produce Authorized result");
        assertTrue(result.isAuthorized());
    }

    /**
     * A {@code 200 OK} response with {@code "AUTHORIZED"} status (case-insensitive) must
     * still produce {@link AuthorizationResult.Authorized}.
     */
    @Test
    void authorize_200WithAuthorizedStatusLowercase_returnsAuthorized() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("50.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"status":"authorized","declineReason":null}
                        """,
                        MediaType.APPLICATION_JSON));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertTrue(result.isAuthorized(), "Status check must be case-insensitive");
    }

    // -------------------------------------------------------------------------
    // authorize() — decline path (402)
    // -------------------------------------------------------------------------

    /**
     * A {@code 402 Payment Required} response must produce {@link AuthorizationResult.Declined}
     * with the provider's decline reason extracted from the response body (Requirement 5.2).
     */
    @Test
    void authorize_402WithDeclineReason_returnsDeclinedWithReason() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("200.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                              {"declineReason":"insufficient_funds"}
                              """));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Declined.class, result,
                "402 should produce Declined result");
        assertFalse(result.isAuthorized());
        assertEquals("insufficient_funds", result.getDeclineReason());
    }

    /**
     * A {@code 402} response with a {@code "reason"} field (alternate key) must still
     * extract the decline reason correctly.
     */
    @Test
    void authorize_402WithReasonField_returnsDeclinedWithReason() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("150.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                              {"reason":"card_expired"}
                              """));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Declined.class, result);
        assertEquals("card_expired", result.getDeclineReason());
    }

    /**
     * A {@code 402} response with an empty body must produce {@link AuthorizationResult.Declined}
     * with a generic fallback reason rather than throwing.
     */
    @Test
    void authorize_402WithEmptyBody_returnsDeclinedWithFallbackReason() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("75.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(""));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Declined.class, result);
        assertFalse(result.getDeclineReason().isBlank(),
                "Fallback decline reason must not be blank");
    }

    // -------------------------------------------------------------------------
    // authorize() — transport failure path
    // -------------------------------------------------------------------------

    /**
     * A transport-level failure (connection refused / I/O error) must produce
     * {@link AuthorizationResult.Failed} rather than propagating the exception
     * (Requirement 5.3).
     */
    @Test
    void authorize_transportFailure_returnsFailedResult() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("30.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Failed.class, result,
                "Non-2xx / transport error should produce Failed result");
        assertFalse(result.isAuthorized());
        assertFalse(result.getDeclineReason().isBlank(),
                "Failed result must carry a non-blank reason");
    }

    /**
     * A {@code 500 Internal Server Error} from the provider must produce
     * {@link AuthorizationResult.Failed}.
     */
    @Test
    void authorize_500ServerError_returnsFailedResult() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("45.00"), USD);

        mockServer.expect(requestTo(BASE_URL + "/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AuthorizationResult result = adapter.authorize(orderId, amount);

        mockServer.verify();
        assertInstanceOf(AuthorizationResult.Failed.class, result);
    }

    // -------------------------------------------------------------------------
    // voidAuthorization() — success path
    // -------------------------------------------------------------------------

    /**
     * A successful {@code DELETE /authorizations/{orderId}} must complete without
     * throwing (Requirement 5.1 — void is best-effort).
     */
    @Test
    void voidAuthorization_success_completesNormally() {
        OrderId orderId = new OrderId(UUID.randomUUID());

        mockServer.expect(requestTo(BASE_URL + "/authorizations/" + orderId.value()))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // Must not throw
        adapter.voidAuthorization(orderId);

        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // voidAuthorization() — transport failure path
    // -------------------------------------------------------------------------

    /**
     * A transport failure during void must be swallowed (logged as warning) and must
     * not propagate — void is best-effort (Requirement 5.3).
     */
    @Test
    void voidAuthorization_transportFailure_doesNotPropagate() {
        OrderId orderId = new OrderId(UUID.randomUUID());

        mockServer.expect(requestTo(BASE_URL + "/authorizations/" + orderId.value()))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // Must not throw even on failure
        adapter.voidAuthorization(orderId);

        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Circuit-breaker fallback methods
    // -------------------------------------------------------------------------

    /**
     * The {@code authorizeFallback} method must return
     * {@link AuthorizationResult#failed(String)} with reason
     * {@code dependency_unavailable:payment} (Requirements 5.4, 5.5, 12.1).
     */
    @Test
    void authorizeFallback_returnsDependencyUnavailableResult() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        Money amount = Money.of(new BigDecimal("10.00"), USD);
        RuntimeException cause = new RuntimeException("circuit open");

        AuthorizationResult result = adapter.authorizeFallback(orderId, amount, cause);

        assertInstanceOf(AuthorizationResult.Failed.class, result,
                "Fallback must return a Failed result");
        assertEquals("dependency_unavailable:payment", result.getDeclineReason(),
                "Fallback reason must be 'dependency_unavailable:payment'");
    }

    /**
     * The {@code voidAuthorizationFallback} method must complete without throwing
     * (void is best-effort; Requirements 5.4, 12.1).
     */
    @Test
    void voidAuthorizationFallback_completesNormally() {
        OrderId orderId = new OrderId(UUID.randomUUID());
        RuntimeException cause = new RuntimeException("circuit open");

        // Must not throw
        adapter.voidAuthorizationFallback(orderId, cause);
    }
}
