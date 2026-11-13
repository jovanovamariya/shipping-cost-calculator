package com.example.shipping.client;

import com.example.shipping.service.RatesProviderException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrankfurterRatesProviderTest {

    @RegisterExtension
    static WireMockExtension frankfurter = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final FrankfurterRatesProvider provider =
            new FrankfurterRatesProvider(RestClient.builder(), frankfurter.baseUrl());

    @Test
    @DisplayName("The one where Frankfurter's currency list is read as code → name")
    void readsCurrenciesAsCodeToName() {
        frankfurter.stubFor(get("/currencies").willReturn(okJson("""
                {
                  "AUD": "Australian Dollar",
                  "USD": "United States Dollar"
                }
                """)));

        assertThat(provider.currencies()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AUD", "Australian Dollar",
                "USD", "United States Dollar"));
    }

    @Test
    @DisplayName("The one where Frankfurter answers 500 and the client reports the provider as unavailable")
    void reportsServerErrorAsProviderFailure() {
        frankfurter.stubFor(get("/currencies").willReturn(serverError()));

        assertThatThrownBy(provider::currencies)
                .isInstanceOf(RatesProviderException.class);
    }
}
