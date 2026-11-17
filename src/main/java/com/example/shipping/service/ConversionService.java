package com.example.shipping.service;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.model.PublishedRates;
import com.example.shipping.model.SupportedCurrency;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversionService {

    private record CachedRates(PublishedRates rates, Instant fetchedAt) {}

    /** Rates paired with a flag indicating whether they came from a stale fallback. */
    private record FetchedRates(PublishedRates rates, boolean stale) {}

    private final RatesProvider ratesProvider;
    private final CurrencyService currencyService;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Optional<Integer> maxStaleAgeDays;
    private final CircuitBreaker circuitBreaker;
    private final Map<String, CachedRates> ratesCache = new HashMap<>();

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService,
                             Clock clock, Duration cacheTtl, Optional<Integer> maxStaleAgeDays,
                             CircuitBreaker circuitBreaker) {
        this.ratesProvider = ratesProvider;
        this.currencyService = currencyService;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
        this.maxStaleAgeDays = maxStaleAgeDays;
        this.circuitBreaker = circuitBreaker;
    }

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService,
                             Clock clock, Duration cacheTtl, Optional<Integer> maxStaleAgeDays) {
        this(ratesProvider, currencyService, clock, cacheTtl, maxStaleAgeDays, neverOpenCircuitBreaker());
    }

    private static CircuitBreaker neverOpenCircuitBreaker() {
        return CircuitBreaker.of("rates-noop", CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(Integer.MAX_VALUE)
                .build());
    }

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService,
                             Clock clock, Duration cacheTtl) {
        this(ratesProvider, currencyService, clock, cacheTtl, Optional.empty());
    }

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService,
                             Clock clock, Optional<Integer> maxStaleAgeDays) {
        this(ratesProvider, currencyService, clock, Duration.ofHours(1), maxStaleAgeDays);
    }

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService, Clock clock) {
        this(ratesProvider, currencyService, clock, Duration.ofHours(1), Optional.empty());
    }

    /** Convenience overload for tests that do not exercise clock-sensitive behaviour. */
    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService) {
        this(ratesProvider, currencyService, Clock.systemUTC());
    }

    public ConversionResult convert(BigDecimal amount, String from, String to) {
        String normFrom = from.toUpperCase();
        String normTo   = to.toUpperCase();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }

        Set<String> supported = supportedCodes();
        requireSupported(normFrom, supported);
        requireSupported(normTo, supported);

        if (normFrom.equals(normTo)) {
            BigDecimal result = amount.setScale(2, RoundingMode.HALF_UP);
            return new ConversionResult(amount, normFrom, normTo, result, BigDecimal.ONE, LocalDate.now(clock), false);
        }

        Instant now = clock.instant();
        FetchedRates fetched = ratesFor(normFrom, now);

        BigDecimal rate = fetched.rates().rates().get(normTo);
        BigDecimal result = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResult(amount, normFrom, normTo, result, rate, fetched.rates().publicationDate(), fetched.stale());
    }

    /**
     * Returns rates for {@code base} and whether they are stale:
     * cache hit (fresh by TTL, within max stale age) → stale=false;
     * cache hit too old by publication date → throws;
     * provider success → stale=false;
     * provider failure with a usable prior cache entry → stale=true;
     * no entry or entry too stale → throws.
     */
    private FetchedRates ratesFor(String base, Instant now) {
        CachedRates cached = ratesCache.get(base);
        if (cached != null && now.isBefore(cached.fetchedAt().plus(cacheTtl))) {
            if (tooStaleToServe(cached.rates())) throw new RatesProviderException(
                    "Cached rates exceed max stale age", new RuntimeException());
            return new FetchedRates(cached.rates(), false);
        }
        try {
            PublishedRates fresh = circuitBreaker.executeSupplier(() -> ratesProvider.latestRates(base));
            ratesCache.put(base, new CachedRates(fresh, now));
            return new FetchedRates(fresh, false);
        } catch (CallNotPermittedException e) {
            if (cached == null || tooStaleToServe(cached.rates())) throw new RatesProviderException(
                    "Exchange rates are temporarily unavailable. Please try again later.", e);
            return new FetchedRates(cached.rates(), true);
        } catch (RatesProviderException e) {
            if (cached == null || tooStaleToServe(cached.rates())) throw e;
            return new FetchedRates(cached.rates(), true);
        }
    }

    /** True when a max stale age is configured and the rates' publication date exceeds it. */
    private boolean tooStaleToServe(PublishedRates rates) {
        return maxStaleAgeDays.isPresent() &&
               ChronoUnit.DAYS.between(rates.publicationDate(), LocalDate.now(clock)) > maxStaleAgeDays.get();
    }

    private Set<String> supportedCodes() {
        return currencyService.supportedCurrencies().stream()
                .map(SupportedCurrency::code)
                .collect(Collectors.toSet());
    }

    private static void requireSupported(String code, Set<String> supported) {
        if (!supported.contains(code)) {
            throw new UnsupportedCurrencyException("Unsupported currency: " + code);
        }
    }
}
