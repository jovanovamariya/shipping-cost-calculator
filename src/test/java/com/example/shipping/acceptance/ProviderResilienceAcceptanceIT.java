package com.example.shipping.acceptance;

import com.example.shipping.support.MutableClock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
// Circuit state and cached rates live in the application context; a fresh context per test keeps tests independent.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(ProviderResilienceAcceptanceIT.TestClockConfig.class)
@DisplayName("Provider Resilience")
class ProviderResilienceAcceptanceIT {

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
            return new MutableClock(Instant.parse("2026-11-17T09:00:00Z"));
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

    private MvcTestResult convert(String amount, String from, String to) {
        return mvc.post().uri("/api/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": %s, "from": "%s", "to": "%s" }
                        """.formatted(amount, from, to))
                .exchange();
    }

    @Nested
    @DisplayName("Must abandon a provider call after 3 seconds and count it as a failure")
    class ThreeSecondTimeout {

        @Test
        @DisplayName("The one where the provider takes 5 s — the call is cut at 3 s, counted as a failure, and the fallback applies")
        void providerTakes5sCallIsCutAt3sAndFallbackApplies() {
            // Warm the cache at 09:00 with a successful fetch
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(okJson("""
                            { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                            """)));
            convert("100", "EUR", "USD");

            // Advance 2 hours past the 1-hour TTL so the next request must go to the provider
            clock.advance(Duration.ofHours(2));

            // Provider now hangs for 5 seconds — the client must cut it at 3 s
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(okJson("""
                            { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                            """).withFixedDelay(5000)));

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean().isTrue();
        }

        @Test
        @DisplayName("The one where the provider answers in 2.9 s — counted as a success")
        void providerAnswersIn2Point9sIsCountedAsSuccess() {
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(okJson("""
                            { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                            """).withFixedDelay(2900)));

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean().isFalse();
        }
    }
}
