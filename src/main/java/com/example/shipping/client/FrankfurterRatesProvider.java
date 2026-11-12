package com.example.shipping.client;

import com.example.shipping.service.RatesProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** {@link RatesProvider} backed by the Frankfurter API. */
public class FrankfurterRatesProvider implements RatesProvider {

    private final RestClient restClient;

    public FrankfurterRatesProvider(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Map<String, String> currencies() {
        return restClient.get()
                .uri("/currencies")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
