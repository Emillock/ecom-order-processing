package com.example.orderprocessing.unit.serialization;

import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.property.serialization.OrderDto;
import com.example.orderprocessing.property.serialization.OrderItemDto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying that malformed JSON is rejected with a descriptive error
 * and never produces a partial {@link com.example.orderprocessing.domain.model.Order}.
 *
 * <p>Covers Requirement 18.4: unknown/missing required fields and bad types must
 * surface as a descriptive error; no partial order object may be returned.
 */
class MalformedJsonRejectionTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    // -------------------------------------------------------------------------
    // Missing required fields
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Missing 'id' field: toDomain() throws because OrderId rejects null")
    void missingIdFieldThrowsDescriptiveError() {
        // JSON with 'id' omitted — Jackson sets the field to null in the record
        String json = """
                {
                  "items": [{"sku":"SKU-1","quantity":1,"unitPriceAmount":"5.0000","unitPriceCurrency":"USD"}],
                  "status": "CREATED",
                  "subtotalAmount": "5.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "5.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        // Deserialization succeeds (id is null in the DTO record), but toDomain() must
        // reject it because OrderId rejects null — verifying no partial Order is produced.
        assertThatThrownBy(() -> {
            OrderDto dto = mapper.readValue(json, OrderDto.class);
            dto.toDomain();   // must throw: OrderId(null) → IllegalArgumentException
        })
                .isInstanceOf(Exception.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("Missing 'status' field: toDomain() throws because OrderBuilder rejects null status")
    void missingStatusFieldThrowsDescriptiveError() {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000001",
                  "items": [{"sku":"SKU-1","quantity":1,"unitPriceAmount":"5.0000","unitPriceCurrency":"USD"}],
                  "subtotalAmount": "5.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "5.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> {
            OrderDto dto = mapper.readValue(json, OrderDto.class);
            dto.toDomain();   // must throw: OrderBuilder.status(null) → IllegalArgumentException
        })
                .isInstanceOf(Exception.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("Missing 'items' field: toDomain() throws because OrderBuilder requires at least one item")
    void missingItemsFieldThrowsDescriptiveError() {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000002",
                  "status": "CREATED",
                  "subtotalAmount": "0.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "0.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> {
            OrderDto dto = mapper.readValue(json, OrderDto.class);
            dto.toDomain();   // must throw: items is null → NullPointerException or IllegalStateException
        })
                .isInstanceOf(Exception.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    // -------------------------------------------------------------------------
    // Bad types
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Non-UUID value for 'id' field produces a JsonMappingException")
    void badTypeForIdFieldThrowsDescriptiveError() {
        String json = """
                {
                  "id": "not-a-uuid",
                  "items": [],
                  "status": "CREATED",
                  "subtotalAmount": "10.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "10.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, OrderDto.class))
                .isInstanceOf(JsonMappingException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("Non-numeric value for 'subtotalAmount' field produces a JsonMappingException")
    void badTypeForAmountFieldThrowsDescriptiveError() {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000003",
                  "items": [],
                  "status": "CREATED",
                  "subtotalAmount": "not-a-number",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "0.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, OrderDto.class))
                .isInstanceOf(JsonMappingException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("Unknown 'status' enum value produces a JsonMappingException")
    void unknownStatusEnumValueThrowsDescriptiveError() {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000004",
                  "items": [],
                  "status": "UNKNOWN_STATUS_XYZ",
                  "subtotalAmount": "10.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "10.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, OrderDto.class))
                .isInstanceOf(JsonMappingException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    @Test
    @DisplayName("Unknown JSON field produces a JsonMappingException (FAIL_ON_UNKNOWN_PROPERTIES=true)")
    void unknownJsonFieldThrowsDescriptiveError() {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000005",
                  "items": [],
                  "status": "CREATED",
                  "subtotalAmount": "10.0000",
                  "subtotalCurrency": "USD",
                  "discountTotalAmount": "0.0000",
                  "discountTotalCurrency": "USD",
                  "taxTotalAmount": "0.0000",
                  "taxTotalCurrency": "USD",
                  "shippingTotalAmount": "0.0000",
                  "shippingTotalCurrency": "USD",
                  "grandTotalAmount": "10.0000",
                  "grandTotalCurrency": "USD",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z",
                  "unexpectedField": "should-be-rejected"
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, OrderDto.class))
                .isInstanceOf(JsonMappingException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("unexpectedField"));
    }

    @Test
    @DisplayName("Completely malformed JSON produces a JsonParseException")
    void completelyMalformedJsonThrowsParseException() {
        String json = "{ this is not valid json at all !!!";

        assertThatThrownBy(() -> mapper.readValue(json, OrderDto.class))
                .isInstanceOf(JsonParseException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).isNotBlank());
    }

    // -------------------------------------------------------------------------
    // Verify no partial Order is produced on success path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid JSON round-trips to a complete OrderDto with all fields preserved")
    void validJsonProducesCompleteOrderDto() throws Exception {
        OrderDto original = new OrderDto(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                List.of(new OrderItemDto("SKU-A", 2, new BigDecimal("12.5000"), "USD")),
                OrderStatus.CREATED,
                new BigDecimal("25.0000"), "USD",
                new BigDecimal("0.0000"), "USD",
                new BigDecimal("2.5000"), "USD",
                new BigDecimal("5.0000"), "USD",
                new BigDecimal("32.5000"), "USD",
                null,
                Instant.parse("2024-06-01T10:00:00Z"),
                Instant.parse("2024-06-01T10:00:00Z"),
                null);

        String json = mapper.writeValueAsString(original);
        OrderDto restored = mapper.readValue(json, OrderDto.class);

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.status()).isEqualTo(original.status());
        assertThat(restored.items()).hasSize(1);
        assertThat(restored.subtotalAmount().compareTo(original.subtotalAmount())).isZero();
        assertThat(restored.grandTotalAmount().compareTo(original.grandTotalAmount())).isZero();
        assertThat(restored.idempotencyKey()).isNull();
        assertThat(restored.failureReason()).isNull();
    }
}
