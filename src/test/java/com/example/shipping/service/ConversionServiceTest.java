package com.example.shipping.service;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.model.PublishedRates;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.shipping.support.MutableClock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.shipping.service.FakeRatesProviders.currenciesFrom;
import static com.example.shipping.service.FakeRatesProviders.ratesFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionServiceTest {

    /** Builds a CurrencyService that knows exactly the given currency codes. */
    private static CurrencyService currenciesOf(String... codes) {
        Map<String, String> codeMap = Arrays.stream(codes)
                .collect(Collectors.toMap(c -> c, c -> c));
        return new CurrencyService(currenciesFrom(() -> codeMap), Clock.systemUTC(), Duration.ofDays(1));
    }

    @Test
    @DisplayName("The one where 100 EUR → USD at 1.0850 (published 2026-11-11) gives 108.50 USD, rate 1.0850, rate date 2026-11-11")
    void convertsAtPublishedRateAndReportsPublicationDate() {
        RatesProvider fakeProvider = ratesFrom(base -> new PublishedRates(
                base, LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850"))));

        ConversionResult conversion = new ConversionService(fakeProvider, currenciesOf("EUR", "USD"))
                .convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(conversion.result()).isEqualByComparingTo("108.50");
        assertThat(conversion.rate()).isEqualByComparingTo("1.0850");
        assertThat(conversion.rateDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(conversion.stale()).isFalse();
    }

    @Test
    @DisplayName("The one where 100 XYZ → USD is rejected with 'Unsupported currency: XYZ'")
    void rejectsUnsupportedFromCurrency() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "XYZ", "USD"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where 100 EUR → XYZ is rejected with 'Unsupported currency: XYZ'")
    void rejectsUnsupportedToCurrency() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "EUR", "XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where lower-case 'eur' and 'usd' are normalised to upper case before validation")
    void normalisesCodesBeforeValidating() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates(
                        base, LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850")))),
                currenciesOf("EUR", "USD"));

        ConversionResult result = service.convert(new BigDecimal("100"), "eur", "usd");

        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("USD");
    }

    @Test
    @DisplayName("The one where XYZ → XYZ is still rejected — unsupported code is checked before the same-currency short-circuit")
    void unsupportedSameCurrencyIsRejected() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "XYZ", "XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where 100 EUR → EUR gives 100.00, rate 1, rate date today, and no provider call is made")
    void sameCurrencyConvertsAtRateOneWithNoProviderCall() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        Clock fixed = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        // If the same-currency branch is absent, the provider is called and returns a wrong rate (0.5),
        // producing 50.00 instead of 100.00 and exposing the missing branch as an assertion failure.
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates(base, LocalDate.of(2020, 1, 1),
                        Map.of("EUR", new BigDecimal("0.5")))),
                currenciesOf("EUR", "USD"),
                fixed);

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "EUR");

        assertThat(result.result()).isEqualByComparingTo("100.00");
        assertThat(result.rate()).isEqualByComparingTo("1");
        assertThat(result.rateDate()).isEqualTo(today);
        assertThat(result.stale()).isFalse();
    }

    @ParameterizedTest(name = "The one where {0} × {1} rounds to {2} (HALF_UP to 2 decimals)")
    @CsvSource({
            "100, 1.08125, 108.13",   // exact half → rounds up; HALF_EVEN would give 108.12
    })
    void roundsResultToTwoDecimalsHalfUp(String amount, String rate, String expected) {
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates(base, LocalDate.of(2026, 11, 16),
                        Map.of("USD", new BigDecimal(rate)))),
                currenciesOf("EUR", "USD"));

        ConversionResult result = service.convert(new BigDecimal(amount), "EUR", "USD");

        assertThat(result.result()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "The one where EUR rates fetched {0} minutes ago — provider called {1} time(s)")
    @CsvSource({
            "59, 1",   // within TTL: cache is fresh, no second call
            "60, 2",   // at the boundary: isBefore is false, cache expires, provider refreshes
    })
    void cacheTTLBoundary(int minutesElapsed, int expectedCalls) {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));
        AtomicInteger calls = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            calls.incrementAndGet();
            return new PublishedRates("EUR", LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850")));
        });
        ConversionService service = new ConversionService(provider, currenciesOf("EUR", "USD"), clock);

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; fetched-at = 09:00
        clock.advance(Duration.ofMinutes(minutesElapsed));
        service.convert(new BigDecimal("100"), "EUR", "USD"); // fresh: reuses; expired: refreshes

        assertThat(calls.get()).isEqualTo(expectedCalls);
    }

    @Test
    @DisplayName("The one where EUR rates were fetched at 09:00, the provider fails at 11:00, and the conversion returns stale: true with rate date 2026-11-11")
    void staleFallbackWhenProviderFailsAfterCacheExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));
        AtomicInteger callCount = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            if (callCount.incrementAndGet() == 1) {
                return new PublishedRates("EUR", LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850")));
            }
            throw new RatesProviderException("provider down", new RuntimeException());
        });
        ConversionService service = new ConversionService(provider, currenciesOf("EUR", "USD"), clock);

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; fetched-at = 09:00
        clock.advance(Duration.ofHours(2));                   // past the 1 h TTL

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(result.stale()).isTrue();
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(result.result()).isEqualByComparingTo("108.50");
    }

    @Test
    @DisplayName("The one where max stale age is 3 days and EUR rates published 4 days ago are too old to serve — throws instead of returning stale")
    void staleRatesExceedingMaxAgeAreRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-10T09:00:00Z")); // today = Tue Nov 10
        AtomicInteger callCount = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            if (callCount.incrementAndGet() == 1) {
                return new PublishedRates("EUR", LocalDate.of(2026, 11, 6), Map.of("USD", new BigDecimal("1.0850")));
            }
            throw new RatesProviderException("provider down", new RuntimeException());
        });
        ConversionService service = new ConversionService(
                provider, currenciesOf("EUR", "USD"), clock, Optional.of(3));

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; rates published Nov 6 (4 days ago)
        clock.advance(Duration.ofHours(2));                   // past the 1 h cache TTL

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "EUR", "USD"))
                .isInstanceOf(RatesProviderException.class);
    }

    @Test
    @DisplayName("The one where EUR rates published 6 days ago were fetched 5 min ago — a recent fetch does not make old rates fresh when max stale age is 3 days")
    void freshCacheWithOldPublicationDateIsRejectedWhenMaxStaleAgeExceeded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z")); // today = Thu Nov 12
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates("EUR", LocalDate.of(2026, 11, 6), Map.of("USD", new BigDecimal("1.0850")))),
                currenciesOf("EUR", "USD"),
                clock,
                Optional.of(3));

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; fetched-at = 09:00; rates published Nov 6 (6 days ago)
        clock.advance(Duration.ofMinutes(5));                  // 5 min later — cache still fresh (< 1 h TTL)

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "EUR", "USD"))
                .isInstanceOf(RatesProviderException.class);
    }

    @Test
    @DisplayName("The one where max stale age is 3 days and EUR rates published exactly 3 days ago are still served stale — age equal to the limit is allowed")
    void staleRatesAtExactLimitAreServed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-09T09:00:00Z")); // today = Mon Nov 9
        AtomicInteger callCount = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            if (callCount.incrementAndGet() == 1) {
                return new PublishedRates("EUR", LocalDate.of(2026, 11, 6), Map.of("USD", new BigDecimal("1.0850")));
            }
            throw new RatesProviderException("provider down", new RuntimeException());
        });
        ConversionService service = new ConversionService(
                provider, currenciesOf("EUR", "USD"), clock, Optional.of(3));

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; rates published Nov 6 (Nov 9 − Nov 6 = 3 days = exactly the limit)
        clock.advance(Duration.ofHours(2));                   // past the 1 h cache TTL

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(result.stale()).isTrue();
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    @DisplayName("The one where no max stale age is configured and EUR rates published 100 days ago are still served stale — unset means no limit")
    void noMaxStaleAgeAlwaysServesStaleRegardlessOfAge() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));
        AtomicInteger callCount = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            if (callCount.incrementAndGet() == 1) {
                return new PublishedRates("EUR", LocalDate.of(2026, 8, 4), Map.of("USD", new BigDecimal("1.0850"))); // 100 days ago
            }
            throw new RatesProviderException("provider down", new RuntimeException());
        });
        ConversionService service = new ConversionService(
                provider, currenciesOf("EUR", "USD"), clock); // no maxStaleAgeDays — Optional.empty() by default

        service.convert(new BigDecimal("100"), "EUR", "USD");
        clock.advance(Duration.ofHours(2)); // past 1 h TTL

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(result.stale()).isTrue();
        assertThat(result.rateDate()).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    @DisplayName("The one where the cache TTL is configured to 2 seconds and rates fetched 3 seconds ago are expired — the provider is called again")
    void cacheTTLIsConfigurableAndExpiresAtCustomDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T09:00:00Z"));
        AtomicInteger calls = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            calls.incrementAndGet();
            return new PublishedRates("EUR", LocalDate.of(2026, 11, 17), Map.of("USD", new BigDecimal("1.0850")));
        });
        ConversionService service = new ConversionService(
                provider, currenciesOf("EUR", "USD"), clock, Duration.ofSeconds(2));

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache at T=0 s
        clock.advance(Duration.ofSeconds(3));                  // 3 s > 2 s TTL → cache expired

        service.convert(new BigDecimal("100"), "EUR", "USD"); // must refresh from provider

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("The one where the circuit is open — provider is not called, stale fallback applies")
    void circuitOpenBypassesProviderAndReturnsStaleFallback() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T09:00:00Z"));
        AtomicInteger callCount = new AtomicInteger(0);
        RatesProvider provider = ratesFrom(base -> {
            callCount.incrementAndGet();
            return new PublishedRates("EUR", LocalDate.of(2026, 11, 17), Map.of("USD", new BigDecimal("1.0850")));
        });
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        ConversionService service = new ConversionService(
                provider, currenciesOf("EUR", "USD"), clock, Duration.ofSeconds(2), Optional.empty(), cb);

        service.convert(new BigDecimal("100"), "EUR", "USD"); // warms cache; callCount = 1

        cb.transitionToOpenState();
        clock.advance(Duration.ofSeconds(3)); // past the 2 s TTL → cache miss on next call

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(result.stale()).isTrue();
        assertThat(callCount.get()).isEqualTo(1); // open circuit blocked the second provider call
    }

    @ParameterizedTest(name = "The one where amount {0} is rejected as not greater than zero")
    @CsvSource({
            "0",
            "-5",
    })
    void rejectsAmountNotGreaterThanZero(String amount) {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal(amount), "EUR", "USD"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }
}
