package com.example.shipping.controller;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.service.ConversionService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
}
