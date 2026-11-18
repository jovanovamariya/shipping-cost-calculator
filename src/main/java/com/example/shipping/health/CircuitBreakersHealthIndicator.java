package com.example.shipping.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes the rates circuit-breaker state under {@code /actuator/health}
 * as {@code $.components.circuitBreakers.details.rates.details.state}.
 *
 * <p>The application is always reported UP — an OPEN circuit means an external
 * provider is unavailable, not that the application itself has failed.
 */
@Component("circuitBreakers")
public class CircuitBreakersHealthIndicator implements HealthIndicator {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakersHealthIndicator(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Health health() {
        Health ratesHealth = Health.up()
                .withDetail("state", circuitBreaker.getState().name())
                .build();
        return Health.up()
                .withDetail("rates", ratesHealth)
                .build();
    }
}
