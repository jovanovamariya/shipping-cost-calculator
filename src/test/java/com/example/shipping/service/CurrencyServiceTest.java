package com.example.shipping.service;

import com.example.shipping.model.SupportedCurrency;
import com.example.shipping.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

    private static final Duration LIST_TTL = Duration.ofHours(24);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));

    @Test
    @DisplayName("The one where the provider's currencies come back sorted alphabetically by code, each with its name")
    void listsSupportedCurrenciesSortedByCode() {
        Map<String, String> unordered = new LinkedHashMap<>();
        unordered.put("USD", "United States Dollar");
        unordered.put("AUD", "Australian Dollar");
        unordered.put("ZAR", "South African Rand");
        unordered.put("BRL", "Brazilian Real");
        unordered.put("BGN", "Bulgarian Lev");
        RatesProvider fakeProvider = () -> unordered;

        var currencies = new CurrencyService(fakeProvider, clock, LIST_TTL).supportedCurrencies();

        assertThat(currencies).containsExactly(
                new SupportedCurrency("AUD", "Australian Dollar"),
                new SupportedCurrency("BGN", "Bulgarian Lev"),
                new SupportedCurrency("BRL", "Brazilian Real"),
                new SupportedCurrency("USD", "United States Dollar"),
                new SupportedCurrency("ZAR", "South African Rand"));
    }

    @ParameterizedTest(name = "The one where the list was fetched {0} ago — provider called again: {2}",
            quoteTextArguments = false)
    @CsvSource({
            "2 h,       PT2H,     false",
            "23 h 59 m, PT23H59M, false",
            "24 h,      PT24H,    true",
    })
    void reusesListUntilTtlExpires(String age, Duration elapsed, boolean providerCalledAgain) {
        AtomicInteger providerCalls = new AtomicInteger();
        RatesProvider countingProvider = () -> {
            providerCalls.incrementAndGet();
            return Map.of("AUD", "Australian Dollar");
        };
        CurrencyService service = new CurrencyService(countingProvider, clock, LIST_TTL);

        service.supportedCurrencies();
        clock.advance(elapsed);
        service.supportedCurrencies();

        assertThat(providerCalls).hasValue(providerCalledAgain ? 2 : 1);
    }
}
