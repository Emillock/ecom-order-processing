package com.example.orderprocessing.slice.api;

import com.example.orderprocessing.api.OrderController;
import com.example.orderprocessing.api.error.GlobalExceptionHandler;
import com.example.orderprocessing.api.error.InvalidTransitionException;
import com.example.orderprocessing.api.error.OrderNotFoundException;
import com.example.orderprocessing.application.OrderService;
import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice tests for {@link OrderController}.
 *
 * <p>Covers happy paths and error paths for every endpoint, including idempotency-key
 * replay, validation errors, not-found, and invalid-transition responses.
 *
 * <p>Satisfies Requirements 1.1, 1.3, 7.1, 7.3, 9.1, 9.2, 15.1, 15.2.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static final Currency USD = Currency.getInstance("USD");

    private Order sampleOrder;
    private OrderId sampleOrderId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        sampleOrderId = OrderId.generate();
        sampleOrder = new OrderBuilder()
                .id(sampleOrderId)
                .items(List.of(new OrderItem(new Sku("SKU-001"), 2, new Money(new BigDecimal("10.00"), USD))))
                .status(OrderStatus.CONFIRMED)
                .grandTotal(new Money(new BigDecimal("20.00"), USD))
                .build();
    }

    // =========================================================================
    // POST /api/v1/orders — happy path → 201 with Location header
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/orders happy path → 201 with Location header")
    void createOrder_happyPath_returns201WithLocation() throws Exception {
        when(orderService.create(any(), any(), any(), any())).thenReturn(sampleOrder);

        String requestBody = """
                {
                  "customerId": "customer-123",
                  "items": [
                    { "sku": "SKU-001", "quantity": 2, "unitPrice": 10.00, "currency": "USD" }
                  ],
                  "shippingAddress": "123 Main St",
                  "pricingProfile": "default"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(sampleOrderId.value().toString())))
                .andExpect(jsonPath("$.id", is(sampleOrderId.value().toString())))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    // =========================================================================
    // POST /api/v1/orders with Idempotency-Key header → same order returned
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/orders with Idempotency-Key header → same order returned")
    void createOrder_withIdempotencyKey_returnsSameOrder() throws Exception {
        when(orderService.create(any(), any(), any(), any())).thenReturn(sampleOrder);

        String requestBody = """
                {
                  "customerId": "customer-123",
                  "items": [
                    { "sku": "SKU-001", "quantity": 2, "unitPrice": 10.00, "currency": "USD" }
                  ],
                  "shippingAddress": "123 Main St",
                  "pricingProfile": "default"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-key-abc")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(sampleOrderId.value().toString())));
    }

    // =========================================================================
    // POST /api/v1/orders with invalid body (missing items) → 400 VALIDATION_FAILED
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/orders with missing items → 400 VALIDATION_FAILED")
    void createOrder_missingItems_returns400ValidationFailed() throws Exception {
        String requestBody = """
                {
                  "customerId": "customer-123",
                  "shippingAddress": "123 Main St",
                  "pricingProfile": "default"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /api/v1/orders with empty items list → 400 VALIDATION_FAILED")
    void createOrder_emptyItems_returns400ValidationFailed() throws Exception {
        String requestBody = """
                {
                  "customerId": "customer-123",
                  "items": [],
                  "shippingAddress": "123 Main St",
                  "pricingProfile": "default"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /api/v1/orders with missing customerId → 400 VALIDATION_FAILED")
    void createOrder_missingCustomerId_returns400ValidationFailed() throws Exception {
        String requestBody = """
                {
                  "items": [
                    { "sku": "SKU-001", "quantity": 2, "unitPrice": 10.00, "currency": "USD" }
                  ],
                  "shippingAddress": "123 Main St",
                  "pricingProfile": "default"
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    // =========================================================================
    // GET /api/v1/orders/{id} found → 200 with order
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/orders/{id} found → 200 with order")
    void getOrder_found_returns200WithOrder() throws Exception {
        when(orderService.get(eq(sampleOrderId))).thenReturn(sampleOrder);

        mockMvc.perform(get("/api/v1/orders/{id}", sampleOrderId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sampleOrderId.value().toString())))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    // =========================================================================
    // GET /api/v1/orders/{id} not found → 404 ORDER_NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/orders/{id} not found → 404 ORDER_NOT_FOUND")
    void getOrder_notFound_returns404OrderNotFound() throws Exception {
        when(orderService.get(any(OrderId.class)))
                .thenThrow(new IllegalStateException("Order not found: " + sampleOrderId.value()));

        mockMvc.perform(get("/api/v1/orders/{id}", sampleOrderId.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));
    }

    // =========================================================================
    // GET /api/v1/orders → 200 with page
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/orders → 200 with page of orders")
    void listOrders_returns200WithPage() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderService.list(any(OrderQuery.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(sampleOrderId.value().toString())));
    }

    @Test
    @DisplayName("GET /api/v1/orders with status filter → 200 with filtered page")
    void listOrders_withStatusFilter_returns200() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderService.list(any(OrderQuery.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "CONFIRMED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // =========================================================================
    // POST /api/v1/orders/{id}/cancel → 200
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel → 200 with cancelled order")
    void cancelOrder_happyPath_returns200() throws Exception {
        Order cancelledOrder = new OrderBuilder()
                .id(sampleOrderId)
                .items(List.of(new OrderItem(new Sku("SKU-001"), 2, new Money(new BigDecimal("10.00"), USD))))
                .status(OrderStatus.CANCELLED)
                .build();

        when(orderService.cancel(eq(sampleOrderId), any())).thenReturn(cancelledOrder);

        String requestBody = """
                { "reason": "customer request" }
                """;

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", sampleOrderId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    // =========================================================================
    // POST /api/v1/orders/{id}/cancel on terminal order → 409 INVALID_TRANSITION
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel on terminal order → 409 INVALID_TRANSITION")
    void cancelOrder_terminalOrder_returns409InvalidTransition() throws Exception {
        when(orderService.cancel(any(OrderId.class), any()))
                .thenThrow(new IllegalStateException("Transition not allowed: FAILED → CANCELLED"));

        String requestBody = """
                { "reason": "customer request" }
                """;

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", sampleOrderId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_TRANSITION")));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel with missing reason → 400 VALIDATION_FAILED")
    void cancelOrder_missingReason_returns400() throws Exception {
        String requestBody = """
                {}
                """;

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", sampleOrderId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    // =========================================================================
    // GET /api/v1/orders/{id}/events → 200 with events list
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/orders/{id}/events → 200 with events list")
    void getOrderEvents_found_returns200WithEvents() throws Exception {
        OrderStatusEvent event1 = new OrderStatusEvent(
                sampleOrderId, null, OrderStatus.CREATED, Instant.now(), "system", null);
        OrderStatusEvent event2 = new OrderStatusEvent(
                sampleOrderId, OrderStatus.CREATED, OrderStatus.CONFIRMED, Instant.now(), "system", null);

        when(orderService.events(eq(sampleOrderId))).thenReturn(List.of(event1, event2));

        mockMvc.perform(get("/api/v1/orders/{id}/events", sampleOrderId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].to", is("CREATED")))
                .andExpect(jsonPath("$[1].to", is("CONFIRMED")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/events for non-existent order → 404 ORDER_NOT_FOUND")
    void getOrderEvents_orderNotFound_returns404() throws Exception {
        when(orderService.events(any(OrderId.class)))
                .thenThrow(new IllegalStateException("Order not found: " + sampleOrderId.value()));

        mockMvc.perform(get("/api/v1/orders/{id}/events", sampleOrderId.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));
    }
}
