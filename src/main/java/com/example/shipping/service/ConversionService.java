package com.example.shipping.service;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.model.PublishedRates;
import com.example.shipping.model.SupportedCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversionService {

    private final RatesProvider ratesProvider;
    private final CurrencyService currencyService;
    private final Clock clock;

    public ConversionService(RatesProvider ratesProvider, CurrencyService currencyService, Clock clock) {
        this.ratesProvider = ratesProvider;
        this.currencyService = currencyService;
        this.clock = clock;
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

        PublishedRates rates = ratesProvider.latestRates(normFrom);
        BigDecimal rate = rates.rates().get(normTo);
        BigDecimal result = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResult(amount, normFrom, normTo, result, rate, rates.publicationDate(), false);
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
