package com.example.shipping.controller;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.service.ConversionService;
import com.example.shipping.service.InvalidAmountException;
import com.example.shipping.service.RatesProviderException;
import com.example.shipping.service.UnsupportedCurrencyException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ConversionService conversionService;

    @Test
    @DisplayName("The one where a conversion request is passed to the service and the result comes back as JSON")
    void returnsConversionAsJson() {
        given(conversionService.convert(argThat(amount -> amount.compareTo(new BigDecimal("100")) == 0), eq("EUR"), eq("USD")))
                .willReturn(new ConversionResult(new BigDecimal("100"), "EUR", "USD",
                        new BigDecimal("108.50"), new BigDecimal("1.0850"), LocalDate.of(2026, 11, 11), false));

        MvcTestResult result = mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": 100, "from": "EUR", "to": "USD" }
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.amount")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("100");
        assertThat(result).bodyJson().extractingPath("$.from").asString().isEqualTo("EUR");
        assertThat(result).bodyJson().extractingPath("$.to").asString().isEqualTo("USD");
        assertThat(result).bodyJson().extractingPath("$.result")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("108.50");
        assertThat(result).bodyJson().extractingPath("$.rate")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("1.0850");
        assertThat(result).bodyJson().extractingPath("$.rateDate").asString().isEqualTo("2026-11-11");
        assertThat(result).bodyJson().extractingPath("$.stale").asBoolean().isFalse();
    }

    @Test
    @DisplayName("The one where an unsupported currency returns status 400, error \"Bad Request\", message, path, timestamp — no trace")
    void unsupportedCurrencyReturnsConsistentErrorShape() {
        willThrow(new UnsupportedCurrencyException("Unsupported currency: XYZ"))
                .given(conversionService).convert(any(), any(), any());

        MvcTestResult result = mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": 100, "from": "XYZ", "to": "USD" }
                        """)
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.status")
                .convertTo(InstanceOfAssertFactories.INTEGER).isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.error").asString()
                .isEqualTo("Bad Request");
        assertThat(result).bodyJson().extractingPath("$.message").asString()
                .isEqualTo("Unsupported currency: XYZ");
        assertThat(result).bodyJson().extractingPath("$.path").asString()
                .isEqualTo("/api/convert");
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString()
                .isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.trace");
    }

    @Test
    @DisplayName("The one where a zero amount returns status 400, error \"Bad Request\", message, path, timestamp — no trace")
    void invalidAmountReturnsConsistentErrorShape() {
        willThrow(new InvalidAmountException("Amount must be greater than zero"))
                .given(conversionService).convert(any(), any(), any());

        MvcTestResult result = mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": 0, "from": "EUR", "to": "USD" }
                        """)
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.status")
                .convertTo(InstanceOfAssertFactories.INTEGER).isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.error").asString()
                .isEqualTo("Bad Request");
        assertThat(result).bodyJson().extractingPath("$.message").asString()
                .isEqualTo("Amount must be greater than zero");
        assertThat(result).bodyJson().extractingPath("$.path").asString()
                .isEqualTo("/api/convert");
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString()
                .isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.trace");
    }

    @Test
    @DisplayName("The one where rates are unavailable returns status 503, error \"Service Unavailable\", message, path, timestamp — no trace")
    void ratesUnavailableReturnsConsistentErrorShape() {
        willThrow(new RatesProviderException("provider down", new RuntimeException("connection refused")))
                .given(conversionService).convert(any(), any(), any());

        MvcTestResult result = mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": 100, "from": "EUR", "to": "USD" }
                        """)
                .exchange();

        assertThat(result).hasStatus(503);
        assertThat(result).bodyJson().extractingPath("$.status")
                .convertTo(InstanceOfAssertFactories.INTEGER).isEqualTo(503);
        assertThat(result).bodyJson().extractingPath("$.error").asString()
                .isEqualTo("Service Unavailable");
        assertThat(result).bodyJson().extractingPath("$.message").asString()
                .isEqualTo(ApiExceptionHandler.RATES_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.path").asString()
                .isEqualTo("/api/convert");
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString()
                .isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.trace");
    }

    @Test
    @DisplayName("The one where an unexpected internal error returns status 500, a generic message, path, timestamp — no internal detail, no trace")
    void unexpectedErrorReturnsConsistentErrorShapeWithoutInternalDetail() {
        willThrow(new RuntimeException("Cannot read field \"scale\" because \"multiplicand\" is null"))
                .given(conversionService).convert(any(), any(), any());

        MvcTestResult result = mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": 100, "from": "EUR", "to": "USD" }
                        """)
                .exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.status")
                .convertTo(InstanceOfAssertFactories.INTEGER).isEqualTo(500);
        assertThat(result).bodyJson().extractingPath("$.error").asString()
                .isEqualTo("Internal Server Error");
        assertThat(result).bodyJson().extractingPath("$.message").asString()
                .doesNotContain("scale", "multiplicand", "java.", "Exception");
        assertThat(result).bodyJson().extractingPath("$.path").asString()
                .isEqualTo("/api/convert");
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString()
                .isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.trace");
    }
}
