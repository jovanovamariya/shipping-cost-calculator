package com.example.shipping.client;

import com.example.shipping.service.RatesProvider;
import com.example.shipping.service.RatesProviderException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        try {
            return restClient.get()
                    .uri("/currencies")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (HttpServerErrorException e) {
            throw new RatesProviderException("Frankfurter answered " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new RatesProviderException("Frankfurter could not be reached", e);
        }
    }
}
