package com.example.orderprocessing.infrastructure.payment;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.port.AuthorizationResult;
import com.example.orderprocessing.domain.port.PaymentPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter that translates calls to {@link PaymentPort} into HTTP requests against
 * an external payment provider, using Spring 6's {@link RestClient}.
 *
 * <p>This class is the Adapter pattern implementation for the payment port
 * (Requirement 13.5). It maps the domain model to the provider's JSON contract and
 * translates HTTP responses back to {@link AuthorizationResult} variants.
 *
 * <p>Circuit-breaker protection (Resilience4j {@code "payment"} instance) is applied
 * via {@code @CircuitBreaker}. When the breaker is OPEN, a
 * {@code CallNotPermittedException} propagates to the pipeline, which transitions the
 * order to {@code FAILED} with reason {@code dependency_unavailable:payment}
 * (Requirements 5.4, 5.5, 12.1).
 */
@Component
public class HttpPaymentAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentAdapter.class);

    private final RestClient restClient;

    /**
     * Constructs the adapter, building a {@link RestClient} pointed at the configured
     * payment provider base URL.
     *
     * @param config the payment client configuration; must not be {@code null}
     */
    @org.springframework.beans.factory.annotation.Autowired
    public HttpPaymentAdapter(PaymentClientConfig config) {
        this(RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build());
    }

    /**
     * Constructor accepting a pre-built {@link RestClient}.
     * Intended for unit testing so tests can inject a mock or test-double client
     * without starting a real HTTP server.
     *
     * @param restClient the REST client to use for outbound calls; must not be {@code null}
     */
    public HttpPaymentAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Requests a payment authorization (pre-authorization / hold) by calling
     * {@code POST /authorizations} on the payment provider.
     *
     * <p>HTTP response mapping:
     * <ul>
     *   <li>{@code 200 OK} with {@code "AUTHORIZED"} status — {@link AuthorizationResult#authorized()}</li>
     *   <li>{@code 402 Payment Required} — {@link AuthorizationResult#declined(String)} with the
     *       provider's decline reason</li>
     *   <li>Any other error or transport failure — {@link AuthorizationResult#failed(String)}</li>
     * </ul>
     *
     * <p>Protected by the {@code "payment"} circuit breaker. When the breaker is OPEN,
     * {@link #authorizeFallback(OrderId, Money, Throwable)} is invoked instead.
     *
     * @param id         the order identifier; must not be {@code null}
     * @param grandTotal the total amount to authorize; must not be {@code null}
     * @return an {@link AuthorizationResult} reflecting the provider's response; never {@code null}
     */
    @Override
    @CircuitBreaker(name = "payment", fallbackMethod = "authorizeFallback")
    public AuthorizationResult authorize(OrderId id, Money grandTotal) {
        log.debug("Authorizing payment for order={}, amount={} {}", id.value(),
                grandTotal.amount(), grandTotal.currency());
        try {
            AuthorizeRequest request = new AuthorizeRequest(
                    id.value().toString(),
                    grandTotal.amount().toPlainString(),
                    grandTotal.currency().getCurrencyCode()
            );

            ResponseEntity<AuthorizeResponse> response = restClient.post()
                    .uri("/authorizations")
                    .body(request)
                    .retrieve()
                    .toEntity(AuthorizeResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                AuthorizeResponse body = response.getBody();
                if (body != null && "AUTHORIZED".equalsIgnoreCase(body.status())) {
                    log.info("Payment authorized for order={}", id.value());
                    return AuthorizationResult.authorized();
                }
                // 200 but unexpected status — treat as declined
                String reason = body != null ? body.declineReason() : "Unknown";
                log.warn("Payment not authorized for order={}, reason={}", id.value(), reason);
                return AuthorizationResult.declined(reason != null ? reason : "Authorization not confirmed by provider");
            }

            log.warn("Unexpected 2xx status {} for order={}", response.getStatusCode(), id.value());
            return AuthorizationResult.failed("Unexpected response status: " + response.getStatusCode());

        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                // 402 Payment Required — provider explicitly declined
                String reason = extractDeclineReason(ex.getResponseBodyAsString());
                log.warn("Payment declined for order={}, reason={}", id.value(), reason);
                return AuthorizationResult.declined(reason);
            }
            log.error("HTTP error authorizing payment for order={}: {}", id.value(), ex.getMessage());
            return AuthorizationResult.failed("HTTP error: " + ex.getStatusCode());

        } catch (RestClientException ex) {
            log.error("Transport error authorizing payment for order={}: {}", id.value(), ex.getMessage());
            return AuthorizationResult.failed("Transport error: " + ex.getMessage());
        }
    }

    /**
     * Fallback invoked by Resilience4j when the {@code "payment"} circuit breaker is OPEN
     * or when {@link #authorize(OrderId, Money)} throws an unhandled exception that trips
     * the breaker.
     *
     * <p>Returns a {@link AuthorizationResult#failed(String)} with reason
     * {@code dependency_unavailable:payment} so the pipeline can transition the order to
     * {@code FAILED} with a stable, machine-readable reason (Requirements 5.4, 5.5, 12.1).
     *
     * @param id         the order identifier
     * @param grandTotal the amount that was to be authorized
     * @param ex         the exception that triggered the fallback
     * @return a failed {@link AuthorizationResult} indicating the payment dependency is unavailable
     */
    public AuthorizationResult authorizeFallback(OrderId id, Money grandTotal, Throwable ex) {
        log.warn("Payment circuit breaker open for order={}: {}", id.value(), ex.getMessage());
        return AuthorizationResult.failed("dependency_unavailable:payment");
    }

    /**
     * Voids a previously issued payment authorization by calling
     * {@code DELETE /authorizations/{orderId}} on the payment provider.
     *
     * <p>A non-2xx response or transport failure is logged as a warning but does not
     * propagate — the provider is expected to treat unknown authorizations as a no-op
     * (idempotent void).
     *
     * <p>Protected by the {@code "payment"} circuit breaker. When the breaker is OPEN,
     * {@link #voidAuthorizationFallback(OrderId, Throwable)} is invoked instead.
     *
     * @param id the order identifier whose authorization should be voided; must not be
     *           {@code null}
     */
    @Override
    @CircuitBreaker(name = "payment", fallbackMethod = "voidAuthorizationFallback")
    public void voidAuthorization(OrderId id) {
        log.debug("Voiding payment authorization for order={}", id.value());
        try {
            restClient.delete()
                    .uri("/authorizations/{orderId}", id.value().toString())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Payment authorization voided for order={}", id.value());
        } catch (RestClientException ex) {
            // Log and swallow — void is best-effort; provider treats unknown as no-op
            log.warn("Failed to void payment authorization for order={}: {}", id.value(), ex.getMessage());
        }
    }

    /**
     * Fallback invoked by Resilience4j when the {@code "payment"} circuit breaker is OPEN
     * during a void-authorization attempt.
     *
     * <p>Logs a warning and returns normally — void is best-effort and the pipeline does
     * not depend on its outcome (Requirements 5.4, 12.1).
     *
     * @param id the order identifier whose authorization was to be voided
     * @param ex the exception that triggered the fallback
     */
    public void voidAuthorizationFallback(OrderId id, Throwable ex) {
        log.warn("Payment circuit breaker open; skipping void authorization for order={}: {}",
                id.value(), ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a human-readable decline reason from a 402 response body.
     * Falls back to a generic message if the body cannot be parsed.
     *
     * @param body the raw JSON response body; may be {@code null} or blank
     * @return a non-null, non-blank decline reason string
     */
    private String extractDeclineReason(String body) {
        if (body == null || body.isBlank()) {
            return "Payment declined by provider";
        }
        // Extract "declineReason" or "reason" field from the JSON body
        String reason = extractJsonStringField(body, "declineReason");
        if (reason == null) {
            reason = extractJsonStringField(body, "reason");
        }
        return reason != null ? reason : "Payment declined by provider";
    }

    /**
     * Minimal extraction of a named string field from a raw JSON body.
     *
     * @param body      the raw JSON string
     * @param fieldName the field name to look up
     * @return the field value, or {@code null} if not found
     */
    private String extractJsonStringField(String body, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int keyIdx = body.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = body.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }
        int quoteOpen = body.indexOf('"', colonIdx + 1);
        if (quoteOpen < 0) {
            return null;
        }
        int quoteClose = body.indexOf('"', quoteOpen + 1);
        if (quoteClose < 0) {
            return null;
        }
        String value = body.substring(quoteOpen + 1, quoteClose).trim();
        return value.isBlank() ? null : value;
    }

    // -------------------------------------------------------------------------
    // Internal request / response records (provider contract)
    // -------------------------------------------------------------------------

    /**
     * JSON request body for {@code POST /authorizations}.
     *
     * @param orderId      the order identifier string
     * @param amount       the authorization amount as a plain decimal string
     * @param currencyCode the ISO 4217 currency code
     */
    record AuthorizeRequest(String orderId, String amount, String currencyCode) {}

    /**
     * JSON response body for a {@code POST /authorizations} call.
     *
     * @param status        the provider-defined status (e.g., {@code "AUTHORIZED"}, {@code "DECLINED"})
     * @param declineReason the decline reason when status is not {@code "AUTHORIZED"}; may be {@code null}
     */
    record AuthorizeResponse(String status, String declineReason) {}
}
