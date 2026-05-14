package com.example.orderprocessing.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that registers a shared {@link ObjectMapper} bean used by
 * both the REST API layer and the cache serialization layer.
 *
 * <p>The mapper is configured to:
 * <ul>
 *   <li>Reject unknown JSON properties ({@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES})
 *       so that schema drift is caught early (Requirement 18.4).</li>
 *   <li>Write {@link java.time.Instant} and other {@code java.time} types as ISO-8601 strings
 *       rather than numeric timestamps, keeping the JSON human-readable and portable.</li>
 *   <li>Register {@link JavaTimeModule} so that {@code java.time} types are handled
 *       correctly during both serialization and deserialization.</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /**
     * Produces the application-wide {@link ObjectMapper} bean shared by the REST
     * controllers and the {@code OrderSerializer}.
     *
     * @return a fully configured, thread-safe {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Reject unknown fields so that schema mismatches surface as errors (Req 18.4)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // Write java.time types as ISO-8601 strings, not epoch milliseconds
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // Enable java.time support (Instant, etc.)
        mapper.registerModule(new JavaTimeModule());

        return mapper;
    }
}
