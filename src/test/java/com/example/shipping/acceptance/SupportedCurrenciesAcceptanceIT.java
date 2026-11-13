package com.example.shipping.acceptance;

import com.example.shipping.support.MutableClock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
// The currency list is cached in the application; a fresh context per test keeps tests independent.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(SupportedCurrenciesAcceptanceIT.TestClockConfig.class)
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

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MutableClock clock;

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

    @Nested
    @DisplayName("Should reuse a currency list fetched within the configured list TTL (default 24 hours)")
    class ReusesListWithinTtl {

        @ParameterizedTest(name = "The one where the list was fetched {0} ago — provider called again: {2}",
                quoteTextArguments = false)
        @CsvSource({
                "2 h,     PT2H,    false",
                "23 h 59 m, PT23H59M, false",
                "24 h,    PT24H,   true",
        })
        void reusesListUntilTtlExpires(String age, Duration elapsed, boolean providerCalledAgain) {
            provider.stubFor(get("/currencies").willReturn(okJson("""
                    { "AUD": "Australian Dollar", "USD": "United States Dollar" }
                    """)));

            assertThat(mvc.get().uri("/api/currencies")).hasStatusOk();
            clock.advance(elapsed);
            assertThat(mvc.get().uri("/api/currencies")).hasStatusOk();

            int expectedProviderCalls = providerCalledAgain ? 2 : 1;
            provider.verify(expectedProviderCalls, getRequestedFor(urlEqualTo("/currencies")));
        }
    }

    @Nested
    @DisplayName("Must keep serving the last loaded list when a refresh fails")
    class ServesLastListWhenRefreshFails {

        @Test
        @DisplayName("The one where the list was fetched 25 h ago, the provider is down, and the old list is still served")
        void servesOldListWhenProviderIsDown() {
            provider.stubFor(get("/currencies").willReturn(okJson("""
                    { "USD": "United States Dollar", "AUD": "Australian Dollar" }
                    """)));
            assertThat(mvc.get().uri("/api/currencies")).hasStatusOk();

            clock.advance(Duration.ofHours(25));
            provider.stubFor(get("/currencies").willReturn(serverError()));

            MvcTestResult result = mvc.get().uri("/api/currencies").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$[*].code").asArray()
                    .containsExactly("AUD", "USD");
        }

        @Test
        @DisplayName("The one where the provider is down on first start and no list was ever loaded — status 503 with \"Exchange rates are temporarily unavailable. Please try again later.\"")
        void answersUnavailableWhenNoListWasEverLoaded() {
            provider.stubFor(get("/currencies").willReturn(serverError()));

            MvcTestResult result = mvc.get().uri("/api/currencies").exchange();

            assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result).bodyJson()
                    .extractingPath("$.message").asString()
                    .isEqualTo("Exchange rates are temporarily unavailable. Please try again later.");
        }
    }
}
