package com.example.shipping.controller;

import com.example.shipping.service.CurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(CurrencyController.class)
@DisplayName("CORS configuration")
class CorsConfigTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CurrencyService currencyService;

    @Test
    @DisplayName("The one where the configured frontend origin is allowed by the CORS policy")
    void configuredFrontendOriginIsAllowed() {
        MvcTestResult result = mvc.options().uri("/api/currencies")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).hasHeader("Access-Control-Allow-Origin", "http://localhost:5173");
    }
}
