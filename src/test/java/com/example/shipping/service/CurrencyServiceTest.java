package com.example.shipping.service;

import com.example.shipping.model.SupportedCurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

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

        var currencies = new CurrencyService(fakeProvider).supportedCurrencies();

        assertThat(currencies).containsExactly(
                new SupportedCurrency("AUD", "Australian Dollar"),
                new SupportedCurrency("BGN", "Bulgarian Lev"),
                new SupportedCurrency("BRL", "Brazilian Real"),
                new SupportedCurrency("USD", "United States Dollar"),
                new SupportedCurrency("ZAR", "South African Rand"));
    }
}
