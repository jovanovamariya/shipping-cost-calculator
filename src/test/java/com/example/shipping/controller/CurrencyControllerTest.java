package com.example.shipping.controller;

import com.example.shipping.model.SupportedCurrency;
import com.example.shipping.service.CurrencyService;
import com.example.shipping.service.RatesProviderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(CurrencyController.class)
class CurrencyControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CurrencyService currencyService;

    @Test
    @DisplayName("The one where the supported currencies are returned as a JSON list of code and name")
    void returnsSupportedCurrenciesAsJson() {
        given(currencyService.supportedCurrencies()).willReturn(List.of(
                new SupportedCurrency("AUD", "Australian Dollar"),
                new SupportedCurrency("USD", "United States Dollar")));

        assertThat(mvc.get().uri("/api/currencies"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        [
                          { "code": "AUD", "name": "Australian Dollar" },
                          { "code": "USD", "name": "United States Dollar" }
                        ]
                        """);
    }

    @Test
    @DisplayName("The one where the provider is down and no list was ever loaded — 503 with the unavailable message")
    void answersServiceUnavailableWhenProviderFails() {
        given(currencyService.supportedCurrencies())
                .willThrow(new RatesProviderException("provider down", null));

        assertThat(mvc.get().uri("/api/currencies"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .extractingPath("$.message").asString()
                .isEqualTo("Exchange rates are temporarily unavailable. Please try again later.");
    }
}
