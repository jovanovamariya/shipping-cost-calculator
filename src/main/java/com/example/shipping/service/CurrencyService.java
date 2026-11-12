package com.example.shipping.service;

import com.example.shipping.model.SupportedCurrency;

import java.util.Comparator;
import java.util.List;

public class CurrencyService {

    private final RatesProvider ratesProvider;

    public CurrencyService(RatesProvider ratesProvider) {
        this.ratesProvider = ratesProvider;
    }

    public List<SupportedCurrency> supportedCurrencies() {
        return ratesProvider.currencies().entrySet().stream()
                .map(entry -> new SupportedCurrency(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SupportedCurrency::code))
                .toList();
    }
}
