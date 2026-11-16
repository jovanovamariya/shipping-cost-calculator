package com.example.shipping.acceptance;

import com.example.shipping.support.MutableClock;
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

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
// Rates and the currency list are cached in the application; a fresh context per test keeps tests independent.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(CurrencyConversionAcceptanceIT.TestClockConfig.class)
@DisplayName("Currency Conversion")
class CurrencyConversionAcceptanceIT {

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
    @DisplayName("Must reject an unsupported currency")
    class RejectsUnsupportedCurrency {

        @Test
        @DisplayName("The one where 100 XYZ → USD is rejected with status 400 and \"Unsupported currency: XYZ\"")
        void rejectsUnsupportedFromCurrency() {
            MvcTestResult result = convert("100", "XYZ", "USD");

            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson()
                    .extractingPath("$.message").asString()
                    .isEqualTo("Unsupported currency: XYZ");
        }
    }

    @Nested
    @DisplayName("Must convert an amount at the current rate and report the result, the rate used and the rate's publication date")
    class ConvertsAtCurrentRate {

        @Test
        @DisplayName("The one where 100 EUR → USD at 1.0850 (published 2026-11-11) gives 108.50 USD, rate 1.0850, rate date 2026-11-11, stale: false")
        void convertsAndReportsRateAndPublicationDate() {
            providerPublishesEurRates("2026-11-11", "1.0850");

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.result")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("108.50");
            assertThat(result).bodyJson().extractingPath("$.rate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("1.0850");
            assertThat(result).bodyJson().extractingPath("$.rateDate").asString()
                    .isEqualTo("2026-11-11");
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean()
                    .isFalse();
        }

        @Test
        @DisplayName("The one where on Monday 2026-11-09 a successful fetch returns Friday 2026-11-06 rates — rate date 2026-11-06 and stale: false, because the provider answered and a weekend gap is normal")
        void reportsPublicationDateNotFetchDate() {
            clock.setTo(Instant.parse("2026-11-09T09:00:00Z"));
            providerPublishesEurRates("2026-11-06", "1.0850");

            MvcTestResult result = convert("100", "EUR", "USD");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.rateDate").asString()
                    .isEqualTo("2026-11-06");
            assertThat(result).bodyJson().extractingPath("$.stale").asBoolean()
                    .isFalse();
        }
    }
}
