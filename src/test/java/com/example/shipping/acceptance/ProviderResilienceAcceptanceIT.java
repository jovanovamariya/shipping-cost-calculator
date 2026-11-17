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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("Must stop calling the provider when 50% or more of the calls in the last 10 minutes failed, with a minimum of 3 calls")
    @TestPropertySource(properties = "rates.provider.cache-ttl=1s")
    class CircuitBreaker {

        @ParameterizedTest(name = "The one where {0} calls and {1} failures makes the circuit {3}")
        @CsvSource({
                "3, 2, true,  OPEN",   // Open: 67% failure rate (exceeds 50%)
                "4, 2, true,  OPEN",   // Open: exactly 50% failure rate
                "3, 1, false, CLOSED", // Closed: 33% failure rate (below 50%)
                "2, 2, false, CLOSED", // Closed: below the 3-call minimum
        })
        void circuitOpensOrStaysClosedBasedOnFailureRate(
                int totalCalls, int failures, boolean expectedOpen, String circuit) {
            // Reset WireMock stubs between parameterized rows; re-register the currencies stub
            provider.resetAll();
            provider.stubFor(get("/currencies").willReturn(okJson("""
                    { "EUR": "Euro", "USD": "United States Dollar" }
                    """)));

            int successes = totalCalls - failures;

            // Successful calls first — build up the success history and warm the stale cache
            for (int i = 0; i < successes; i++) {
                provider.stubFor(get(urlPathEqualTo("/latest"))
                        .withQueryParam("base", equalTo("EUR"))
                        .willReturn(okJson("""
                                { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                                """)));
                convert("100", "EUR", "USD");
                clock.advance(Duration.ofSeconds(2)); // bypass the configured 1 s cache TTL
            }

            // Failure calls — each must bypass the cache to reach (and fail) the provider
            for (int i = 0; i < failures; i++) {
                provider.stubFor(get(urlPathEqualTo("/latest"))
                        .withQueryParam("base", equalTo("EUR"))
                        .willReturn(serverError()));
                convert("100", "EUR", "USD");
                clock.advance(Duration.ofSeconds(2)); // bypass the 1 s cache TTL
            }

            // Verification: set provider to succeed, make one final call
            provider.stubFor(get(urlPathEqualTo("/latest"))
                    .withQueryParam("base", equalTo("EUR"))
                    .willReturn(okJson("""
                            { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                            """)));

            MvcTestResult result = convert("100", "EUR", "USD");

            // Circuit OPEN  → provider bypassed, stale fallback served (stale: true)
            // Circuit CLOSED → provider called,  fresh rates returned  (stale: false)
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$.stale").asBoolean()
                    .isEqualTo(expectedOpen);
        }
    }

    @Nested
    @DisplayName("Must allow one trial call once the circuit has been open for 5 minutes")
    @TestPropertySource(properties = "rates.provider.cache-ttl=1s")
    class HalfOpenTrialCall {

        @Autowired
        private io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

        @ParameterizedTest(name = "The one where {3}")
        @CsvSource({
                "false, false, true,  'the circuit is still open — no trial call permitted, stale rates served'",
                "true,  false, false, 'the open period has elapsed and the trial call succeeds — circuit closes, fresh rates returned'",
                "true,  true,  true,  'the open period has elapsed and the trial call fails — circuit reopens, stale rates again'",
        })
        void trialCallAfterOpenPeriod(
                boolean simulateHalfOpen, boolean trialFails, boolean expectedStale, String label) {
            provider.resetAll();
            provider.stubFor(get("/currencies").willReturn(okJson(
                    "{ \"EUR\": \"Euro\", \"USD\": \"United States Dollar\" }")));

            // Trip the circuit: 1 success + 2 failures = 3 calls, 67% → OPEN
            provider.stubFor(get(urlPathEqualTo("/latest")).withQueryParam("base", equalTo("EUR"))
                    .willReturn(okJson("""
                            { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                            """)));
            convert("100", "EUR", "USD"); // call 1: success — warms stale cache
            clock.advance(Duration.ofSeconds(2));

            provider.stubFor(get(urlPathEqualTo("/latest")).withQueryParam("base", equalTo("EUR"))
                    .willReturn(serverError()));
            convert("100", "EUR", "USD"); // call 2: failure (2 calls — below 3-call minimum)
            clock.advance(Duration.ofSeconds(2));
            convert("100", "EUR", "USD"); // call 3: failure (3 calls, 67%) → circuit OPENS
            clock.advance(Duration.ofSeconds(2));

            if (simulateHalfOpen) {
                circuitBreaker.transitionToHalfOpenState(); // simulate 5-minute wait elapsed
            }

            // Stub the provider for the trial call (or no trial if circuit still OPEN)
            if (trialFails) {
                provider.stubFor(get(urlPathEqualTo("/latest")).withQueryParam("base", equalTo("EUR"))
                        .willReturn(serverError()));
            } else {
                provider.stubFor(get(urlPathEqualTo("/latest")).withQueryParam("base", equalTo("EUR"))
                        .willReturn(okJson("""
                                { "amount": 1.0, "base": "EUR", "date": "2026-11-17", "rates": { "USD": 1.0850 } }
                                """)));
            }

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$.stale").asBoolean()
                    .isEqualTo(expectedStale);
        }
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
