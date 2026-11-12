package com.example.shipping.controller;

import com.example.shipping.model.SupportedCurrency;
import com.example.shipping.service.CurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
}
