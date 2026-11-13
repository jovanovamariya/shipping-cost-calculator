package com.example.shipping.service;

import com.example.shipping.model.SupportedCurrency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class CurrencyService {

    private final RatesProvider ratesProvider;
    private final Clock clock;
    private final Duration listTtl;

    private List<SupportedCurrency> cachedList;
    private Instant fetchedAt;

    public CurrencyService(RatesProvider ratesProvider, Clock clock, Duration listTtl) {
        this.ratesProvider = ratesProvider;
        this.clock = clock;
        this.listTtl = listTtl;
    }

    public synchronized List<SupportedCurrency> supportedCurrencies() {
        Instant now = clock.instant();
        if (!cachedListIsFresh(now)) {
            cachedList = fetchSortedList();
            fetchedAt = now;
        }
        return cachedList;
    }

    private boolean cachedListIsFresh(Instant now) {
        return cachedList != null && now.isBefore(fetchedAt.plus(listTtl));
    }

    private List<SupportedCurrency> fetchSortedList() {
        return ratesProvider.currencies().entrySet().stream()
                .map(entry -> new SupportedCurrency(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SupportedCurrency::code))
                .toList();
    }
}
