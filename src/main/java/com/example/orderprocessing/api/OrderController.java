package com.example.orderprocessing.api;

import com.example.orderprocessing.api.dto.CancelRequest;
import com.example.orderprocessing.api.dto.CreateOrderRequest;
import com.example.orderprocessing.api.dto.OrderResponse;
import com.example.orderprocessing.api.dto.OrderStatusEventResponse;
import com.example.orderprocessing.api.dto.OrderSummary;
import com.example.orderprocessing.api.error.InvalidTransitionException;
import com.example.orderprocessing.api.error.OrderNotFoundException;
import com.example.orderprocessing.application.OrderService;
import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.OrderQuery;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller exposing the order management API under {@code /api/v1/orders}.
 *
 * <p>Delegates all business logic to {@link OrderService} and maps domain exceptions
 * to HTTP status codes via {@link com.example.orderprocessing.api.error.GlobalExceptionHandler}.
 * DTOs are mapped to/from domain objects within this class, keeping the service layer
 * free of HTTP concerns (SRP, Requirement 14.3).
 *
 * <p>Satisfies Requirements 1.1, 1.3, 7.1, 9.1, 9.2, 15.1.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Constructs an {@code OrderController} with the required service facade.
     *
     * @param orderService the order service facade; must not be {@code null}
     */
    public OrderController(OrderService orderService) {
        if (orderService == null) throw new IllegalArgumentException("orderService must not be null");
        this.orderService = orderService;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/orders
    // -------------------------------------------------------------------------

    /**
     * Creates a new order, optionally enforcing idempotency via the {@code Idempotency-Key} header.
     *
     * @param idempotencyKeyHeader the optional client-supplied deduplication key
     * @param request              the validated create-order request body
     * @return 201 Created with the order response and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request) {

        Optional<IdempotencyKey> idempotencyKey = Optional.ofNullable(idempotencyKeyHeader)
                .filter(s -> !s.isBlank())
                .map(IdempotencyKey::new);

        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        new com.example.orderprocessing.domain.model.Sku(item.sku()),
                        item.quantity(),
                        new Money(item.unitPrice(), Currency.getInstance(item.currency()))))
                .toList();

        Order order = orderService.create(items, request.customerId(), request.pricingProfile(), idempotencyKey);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId().value())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/orders/{id}
    // -------------------------------------------------------------------------

    /**
     * Retrieves a single order by its identifier.
     *
     * @param id the UUID of the order to retrieve
     * @return 200 OK with the full order response, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        try {
            Order order = orderService.get(new OrderId(id));
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (IllegalStateException ex) {
            throw new OrderNotFoundException("Order not found: " + id);
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/orders
    // -------------------------------------------------------------------------

    /**
     * Lists orders matching the supplied filter criteria with pagination.
     *
     * @param status     optional status filter
     * @param customerId optional customer identifier filter
     * @param from       optional inclusive lower bound on {@code createdAt}
     * @param to         optional inclusive upper bound on {@code createdAt}
     * @param page       zero-based page index (default 0)
     * @param size       page size (default 20)
     * @return 200 OK with a page of order summaries
     */
    @GetMapping
    public ResponseEntity<Page<OrderSummary>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                orderStatus = OrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid status value: " + status);
            }
        }

        OrderQuery query = new OrderQuery(orderStatus, customerId, from, to);
        Pageable pageable = PageRequest.of(page, size);

        Page<Order> orders = orderService.list(query, pageable);
        Page<OrderSummary> summaries = orders.map(OrderSummary::from);

        return ResponseEntity.ok(summaries);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/orders/{id}/cancel
    // -------------------------------------------------------------------------

    /**
     * Cancels an existing order with the supplied reason.
     *
     * @param id      the UUID of the order to cancel
     * @param request the validated cancel request body containing the reason
     * @return 200 OK with the updated order response
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelRequest request) {

        try {
            Order cancelled = orderService.cancel(new OrderId(id), request.reason());
            return ResponseEntity.ok(OrderResponse.from(cancelled));
        } catch (IllegalStateException ex) {
            String message = ex.getMessage();
            if (message != null && message.startsWith("Order not found")) {
                throw new OrderNotFoundException(message);
            }
            throw new InvalidTransitionException(message != null ? message : "Invalid transition", ex);
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/orders/{id}/events
    // -------------------------------------------------------------------------

    /**
     * Returns the full status-transition audit event log for the given order.
     *
     * @param id the UUID of the order whose events are requested
     * @return 200 OK with the list of status events
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<OrderStatusEventResponse>> getOrderEvents(@PathVariable UUID id) {
        try {
            List<OrderStatusEvent> events = orderService.events(new OrderId(id));
            List<OrderStatusEventResponse> response = events.stream()
                    .map(OrderStatusEventResponse::from)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            throw new OrderNotFoundException("Order not found: " + id);
        }
    }
}
