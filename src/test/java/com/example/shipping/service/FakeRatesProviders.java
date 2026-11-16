package com.example.shipping.service;

import com.example.shipping.model.PublishedRates;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Builds fake {@link RatesProvider}s that answer only the call a test is about. */
final class FakeRatesProviders {

    private FakeRatesProviders() {
    }

    static RatesProvider currenciesFrom(Supplier<Map<String, String>> currencies) {
        return new RatesProvider() {
            @Override
            public Map<String, String> currencies() {
                return currencies.get();
            }

            @Override
            public PublishedRates latestRates(String base) {
                throw new UnsupportedOperationException("This fake only provides currencies");
            }
        };
    }

    static RatesProvider ratesFrom(Function<String, PublishedRates> rates) {
        return new RatesProvider() {
            @Override
            public Map<String, String> currencies() {
                throw new UnsupportedOperationException("This fake only provides rates");
            }

            @Override
            public PublishedRates latestRates(String base) {
                return rates.apply(base);
            }
        };
    }
}
