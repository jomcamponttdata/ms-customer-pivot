package com.pivot.banking.customer.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Unit tests for correlation identifier propagation. */
class CorrelationIdWebFilterTest {

    @Test
    void propagatesIncomingCorrelationIdToResponseAndContext() {
        // Arrange
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers")
                .header("X-Correlation-ID", "customer-correlation-1").build());
        AtomicReference<ContextView> captured = new AtomicReference<>();
        WebFilterChain chain = ignored -> Mono.deferContextual(context -> {
            captured.set(context);
            return Mono.empty();
        });

        // Act
        Mono<Void> result = new CorrelationIdWebFilter().filter(exchange, chain);

        // Assert
        StepVerifier.create(result).verifyComplete();
        assertEquals("customer-correlation-1",
                exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"));
        assertEquals("customer-correlation-1", captured.get().get("correlationId"));
    }

    @Test
    void generatesIdWhenHeaderIsMissing() {
        // Arrange
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers").build());
        AtomicReference<ContextView> captured = new AtomicReference<>();
        WebFilterChain chain = ignored -> Mono.deferContextual(context -> {
            captured.set(context);
            return Mono.empty();
        });

        // Act
        Mono<Void> result = new CorrelationIdWebFilter().filter(exchange, chain);

        // Assert
        StepVerifier.create(result).verifyComplete();
        String generated = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertNotNull(generated);
        assertEquals(generated, captured.get().get("correlationId"));
    }

    @Test
    void replacesBlankIncomingId() {
        // Arrange
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers")
                .header("X-Correlation-ID", " ").build());

        // Act
        Mono<Void> result = new CorrelationIdWebFilter().filter(exchange, ignored -> Mono.empty());

        // Assert
        StepVerifier.create(result).verifyComplete();
        assertNotNull(exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"));
        assertEquals(false, exchange.getResponse().getHeaders().getFirst("X-Correlation-ID").isBlank());
    }
}
