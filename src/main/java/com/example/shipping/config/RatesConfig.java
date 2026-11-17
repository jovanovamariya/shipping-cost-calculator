package com.example.shipping.config;

import com.example.shipping.client.FrankfurterRatesProvider;
import com.example.shipping.service.ConversionService;
import com.example.shipping.service.CurrencyService;
import com.example.shipping.service.RatesProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Configuration
public class RatesConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RatesProvider ratesProvider(RestClient.Builder restClientBuilder,
                                @Value("${rates.provider.base-url}") String baseUrl,
                                @Value("${rates.provider.timeout:3s}") Duration timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        factory.setConnectTimeout(timeout);
        return new FrankfurterRatesProvider(restClientBuilder.requestFactory(factory), baseUrl);
    }

    @Bean
    CurrencyService currencyService(RatesProvider ratesProvider, Clock clock,
                                    @Value("${currencies.list-ttl}") Duration listTtl) {
        return new CurrencyService(ratesProvider, clock, listTtl);
    }

    @Bean
    CircuitBreaker circuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(600) // 10-minute window (in seconds)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        return CircuitBreaker.of("rates", config);
    }

    @Bean
    ConversionService conversionService(RatesProvider ratesProvider, CurrencyService currencyService, Clock clock,
                                        @Value("${rates.max-stale-age-days:-1}") int maxStaleAgeConfig,
                                        @Value("${rates.provider.cache-ttl:1h}") Duration cacheTtl,
                                        CircuitBreaker circuitBreaker) {
        Optional<Integer> maxStaleAge = maxStaleAgeConfig < 0 ? Optional.empty() : Optional.of(maxStaleAgeConfig);
        return new ConversionService(ratesProvider, currencyService, clock, cacheTtl, maxStaleAge, circuitBreaker);
    }
}
