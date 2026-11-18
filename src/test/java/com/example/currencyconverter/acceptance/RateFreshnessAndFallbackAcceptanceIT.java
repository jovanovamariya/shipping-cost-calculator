package com.example.currencyconverter.acceptance;

import com.example.currencyconverter.support.MutableClock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
// Rates are cached in the application; a fresh context per test keeps tests independent.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(RateFreshnessAndFallbackAcceptanceIT.TestClockConfig.class)
@DisplayName("Rate Freshness and Fallback")
class RateFreshnessAndFallbackAcceptanceIT {

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

    @BeforeEach
    void providerSupportsEurAndUsd() {
        provider.stubFor(get("/currencies").willReturn(okJson("""
                { "EUR": "Euro", "USD": "United States Dollar" }
                """)));
    }

    private void providerPublishesEurRates(String publicationDate, String usdRate) {
        provider.stubFor(get(urlPathEqualTo("/latest"))
                .withQueryParam("base", equalTo("EUR"))
                .willReturn(okJson("""
                        { "amount": 1.0, "base": "EUR", "date": "%s", "rates": { "USD": %s } }
                        """.formatted(publicationDate, usdRate))));
    }

    private MvcTestResult convert(String amount, String from, String to) {
        return mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": %s, "from": "%s", "to": "%s" }
                        """.formatted(amount, from, to))
                .exchange();
    }

    @Nested
    @DisplayName("Cache TTL is configurable via rates.provider.cache-ttl")
    @TestPropertySource(properties = "rates.provider.cache-ttl=1s")
    class ConfigurableCacheTtl {

        @Test
        @DisplayName("The one where cache-ttl is 1 s and 2 seconds pass — provider is called again with the latest rate")
        void providerIsCalledAgainAfterConfiguredCacheTtlElapses() {
            // Warm cache at 09:00 with rate 1.0850
            providerPublishesEurRates("2026-11-17", "1.0850");
            convert("100", "EUR", "USD");

            // Advance past the 1 s TTL, then publish a different rate
            clock.advance(Duration.ofSeconds(2));
            providerPublishesEurRates("2026-11-17", "1.2000"); // LIFO: this stub now wins

            // Cache expired → provider called → result 120.00
            // Cache still fresh (default 1 h) → cached → result 108.50
            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$.result")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("120.00");
        }
    }

    @Nested
    @DisplayName("Must not serve fallback rates whose publication date is older than the configured maximum stale age")
    @TestPropertySource(properties = "rates.max-stale-age-days=3")
    class MaxStaleAge {

        @Test
        @DisplayName("The one where EUR rates published 2026-11-06 are served on 2026-11-10 after the provider fails — 4 days exceeds the 3-day limit, so 503 instead of stale fallback")
        void ratesOlderThanMaxStaleAgeAreRejectedWith503WhenProviderFails() {
            // Warm cache on Nov 06; cache expires well before we reach Nov 10
            clock.setTo(Instant.parse("2026-11-06T09:00:00Z"));
            providerPublishesEurRates("2026-11-06", "1.0850");
            convert("100", "EUR", "USD");

            // Nov 10: TTL expired (4 days > 1h), age = 4 days > 3-day limit
            clock.setTo(Instant.parse("2026-11-10T11:00:00Z"));
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(serverError()));

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatus(503);
            assertThat(result).bodyJson()
                    .extractingPath("$.message").asString()
                    .isEqualTo("Exchange rates are temporarily unavailable. Please try again later.");
        }

        @Test
        @DisplayName("The one where EUR rates published 2026-11-06 were fetched on 2026-11-12 — a recent fetch does not make 6-day-old rates fresh when max stale age is 3 days")
        void freshCacheWithOldPublicationDateIsRejectedWith503() {
            // Warm cache on Nov 12 with rates the provider returns dated 2026-11-06
            clock.setTo(Instant.parse("2026-11-12T09:00:00Z"));
            providerPublishesEurRates("2026-11-06", "1.0850");
            convert("100", "EUR", "USD"); // fetched-at = Nov 12 09:00; published Nov 06 (6 days ago)

            // 5 min later: cache TTL still fresh (5 min < 1h), but publication date is 6 days old
            clock.setTo(Instant.parse("2026-11-12T09:05:00Z"));

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatus(503);
            assertThat(result).bodyJson()
                    .extractingPath("$.message").asString()
                    .isEqualTo("Exchange rates are temporarily unavailable. Please try again later.");
        }
    }

    @Nested
    @DisplayName("Must fall back to the last known rates for that base, marked stale, when the provider fails or the circuit is open")
    class StaleRatesFallback {

        @Test
        @DisplayName("The one where EUR rates published 2026-11-11 were fetched at 09:00, the provider fails at 11:00, and 100 EUR → USD returns stale: true with rate date 2026-11-11")
        void providerFailureAfterCacheExpiryReturnsStaleFlagAndLastKnownRateDate() {
            // Clock starts at 09:00; warm the cache with a successful fetch (fetched-at = 09:00)
            providerPublishesEurRates("2026-11-11", "1.0850");
            convert("100", "EUR", "USD");

            // Advance 2 hours past the 1-hour TTL, then make the provider fail
            clock.advance(Duration.ofHours(2)); // now 11:00
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(serverError()));

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean().isTrue();
            assertThat(result).bodyJson().extractingPath("$.rateDate").asString().isEqualTo("2026-11-11");
            assertThat(result).bodyJson().extractingPath("$.result")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("108.50");
        }

        @Test
        @DisplayName("The one where the provider answers normally — stale: false, whatever the publication date")
        void normalProviderResponseReturnsStaleFalse() {
            providerPublishesEurRates("2026-11-11", "1.0850");

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean().isFalse();
        }
    }
}
