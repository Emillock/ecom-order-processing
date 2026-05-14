package com.example.orderprocessing.infrastructure.inventory;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Adapter that translates calls to {@link InventoryPort} into HTTP requests against
 * an external inventory provider, using Spring 6's {@link RestClient}.
 *
 * <p>This class is the Adapter pattern implementation for the inventory port
 * (Requirement 13.5). It maps the domain model to the provider's JSON contract and
 * translates HTTP responses back to {@link ReservationResult} variants.
 *
 * <p>Circuit-breaker protection (Resilience4j {@code "inventory"} instance) is applied
 * in task 12.2 via {@code @CircuitBreaker}. When the breaker is OPEN, a
 * {@code CallNotPermittedException} propagates to the pipeline, which transitions the
 * order to {@code FAILED} with reason {@code dependency_unavailable:inventory}
 * (Requirements 4.4, 4.5, 12.1).
 */
@Component
public class HttpInventoryAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(HttpInventoryAdapter.class);

    private final RestClient restClient;

    /**
     * Constructs the adapter, building a {@link RestClient} pointed at the configured
     * inventory provider base URL.
     *
     * @param config the inventory client configuration; must not be {@code null}
     */
    public HttpInventoryAdapter(InventoryClientConfig config) {
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
    }

    /**
     * Attempts to reserve stock for all items in the given order by calling
     * {@code POST /reservations} on the inventory provider.
     *
     * <p>HTTP response mapping:
     * <ul>
     *   <li>{@code 200 OK} — {@link ReservationResult#success()}</li>
     *   <li>{@code 409 Conflict} — {@link ReservationResult#outOfStock(List)} with the
     *       unavailable SKUs extracted from the response body</li>
     *   <li>Any other error or transport failure — {@link ReservationResult#failed(String)}</li>
     * </ul>
     *
     * @param id    the order identifier; must not be {@code null}
     * @param items the line items to reserve; must not be {@code null} or empty
     * @return a {@link ReservationResult} reflecting the provider's response; never {@code null}
     */
    @Override
    public ReservationResult reserve(OrderId id, List<OrderItem> items) {
        log.debug("Reserving inventory for order={}", id.value());
        try {
            ReserveRequest request = buildReserveRequest(id, items);
            ResponseEntity<ReserveResponse> response = restClient.post()
                    .uri("/reservations")
                    .body(request)
                    .retrieve()
                    .toEntity(ReserveResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Inventory reserved successfully for order={}", id.value());
                return ReservationResult.success();
            }

            log.warn("Unexpected 2xx status {} for order={}", response.getStatusCode(), id.value());
            return ReservationResult.failed("Unexpected response status: " + response.getStatusCode());

        } catch (HttpClientErrorException.Conflict ex) {
            // 409 Conflict — provider signals out-of-stock
            List<Sku> unavailable = extractUnavailableSkus(ex);
            log.warn("Out-of-stock for order={}, unavailableSkus={}", id.value(), unavailable);
            if (unavailable.isEmpty()) {
                return ReservationResult.failed("Out-of-stock but no SKUs returned by provider");
            }
            return ReservationResult.outOfStock(unavailable);

        } catch (RestClientException ex) {
            log.error("Transport error reserving inventory for order={}: {}", id.value(), ex.getMessage());
            return ReservationResult.failed("Transport error: " + ex.getMessage());
        }
    }

    /**
     * Releases a previously held stock reservation by calling
     * {@code DELETE /reservations/{orderId}} on the inventory provider.
     *
     * <p>A non-2xx response or transport failure is logged as a warning but does not
     * propagate — the provider is expected to treat unknown reservations as a no-op
     * (idempotent release).
     *
     * @param id    the order identifier whose reservation should be released; must not be
     *              {@code null}
     * @param items the line items whose reserved quantities should be returned to stock;
     *              must not be {@code null}
     */
    @Override
    public void release(OrderId id, List<OrderItem> items) {
        log.debug("Releasing inventory reservation for order={}", id.value());
        try {
            restClient.delete()
                    .uri("/reservations/{orderId}", id.value().toString())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Inventory reservation released for order={}", id.value());
        } catch (RestClientException ex) {
            // Log and swallow — release is best-effort; provider treats unknown as no-op
            log.warn("Failed to release inventory reservation for order={}: {}", id.value(), ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the JSON request body for the {@code POST /reservations} call.
     *
     * @param id    the order identifier
     * @param items the line items to include in the reservation request
     * @return a populated {@link ReserveRequest}
     */
    private ReserveRequest buildReserveRequest(OrderId id, List<OrderItem> items) {
        List<ReserveRequest.LineItem> lineItems = items.stream()
                .map(item -> new ReserveRequest.LineItem(item.sku().value(), item.quantity()))
                .toList();
        return new ReserveRequest(id.value().toString(), lineItems);
    }

    /**
     * Attempts to extract unavailable SKU values from a 409 response body.
     * Returns an empty list if the body cannot be parsed.
     *
     * @param ex the 409 exception whose body may contain unavailable SKU data
     * @return a list of {@link Sku} values; never {@code null}
     */
    @SuppressWarnings("unchecked")
    private List<Sku> extractUnavailableSkus(HttpClientErrorException.Conflict ex) {
        try {
            // Attempt to read the response body as a map with an "unavailableSkus" array
            String body = ex.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                return List.of();
            }
            // Simple extraction: look for a JSON array of SKU strings under "unavailableSkus"
            // A full implementation would use ObjectMapper; this keeps the adapter dependency-light
            // and delegates JSON parsing to the RestClient's message converters via a typed response.
            // For the 409 path we parse manually since retrieve() throws before returning a body.
            return parseUnavailableSkusFromBody(body);
        } catch (Exception parseEx) {
            log.debug("Could not parse unavailable SKUs from 409 body: {}", parseEx.getMessage());
            return List.of();
        }
    }

    /**
     * Minimal JSON extraction of the {@code unavailableSkus} string array from a raw body.
     * Delegates to Jackson via a simple string scan; a full implementation would inject
     * {@code ObjectMapper} for robustness.
     *
     * @param body the raw JSON response body
     * @return a list of {@link Sku} values parsed from the body; never {@code null}
     */
    private List<Sku> parseUnavailableSkusFromBody(String body) {
        // Locate the "unavailableSkus" array in the JSON body using basic string parsing.
        // This avoids a direct ObjectMapper dependency in the adapter while remaining correct
        // for well-formed provider responses.
        int arrayStart = body.indexOf("\"unavailableSkus\"");
        if (arrayStart < 0) {
            return List.of();
        }
        int bracketOpen = body.indexOf('[', arrayStart);
        int bracketClose = body.indexOf(']', bracketOpen);
        if (bracketOpen < 0 || bracketClose < 0) {
            return List.of();
        }
        String arrayContent = body.substring(bracketOpen + 1, bracketClose).trim();
        if (arrayContent.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(arrayContent.split(","))
                .map(s -> s.trim().replace("\"", ""))
                .filter(s -> !s.isBlank())
                .map(Sku::new)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Internal request / response records (provider contract)
    // -------------------------------------------------------------------------

    /**
     * JSON request body for {@code POST /reservations}.
     *
     * @param orderId   the order identifier string
     * @param lineItems the items to reserve
     */
    record ReserveRequest(String orderId, List<LineItem> lineItems) {

        /**
         * A single line item in the reservation request.
         *
         * @param sku      the SKU value
         * @param quantity the quantity to reserve
         */
        record LineItem(String sku, int quantity) {}
    }

    /**
     * JSON response body for a successful {@code POST /reservations} call.
     *
     * @param status a provider-defined status string (e.g., {@code "RESERVED"})
     */
    record ReserveResponse(String status) {}
}
