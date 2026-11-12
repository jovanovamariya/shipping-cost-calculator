package com.example.shipping.acceptance;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Supported Currencies")
class SupportedCurrenciesAcceptanceIT {

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("rates.provider.base-url", provider::baseUrl);
    }

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("Should list every supported currency, sorted alphabetically by code")
    class ListsCurrenciesSortedByCode {

        @Test
        @DisplayName("The one where the list reads AUD, BGN, BRL … USD, ZAR, each with its name (AUD — \"Australian Dollar\")")
        void listsEveryCurrencySortedByCodeWithItsName() {
            // The provider answers in no particular order; sorting is our job.
            provider.stubFor(get("/currencies").willReturn(okJson("""
                    {
                      "USD": "United States Dollar",
                      "AUD": "Australian Dollar",
                      "ZAR": "South African Rand",
                      "BRL": "Brazilian Real",
                      "BGN": "Bulgarian Lev"
                    }
                    """)));

            MvcTestResult result = mvc.get().uri("/api/currencies").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$[*].code").asArray()
                    .containsExactly("AUD", "BGN", "BRL", "USD", "ZAR");
            assertThat(result).bodyJson()
                    .extractingPath("$[0].name").asString()
                    .isEqualTo("Australian Dollar");
        }
    }
}
