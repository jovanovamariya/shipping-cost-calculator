package com.example.currencyconverter.client;

import com.example.currencyconverter.model.PublishedRates;
import com.example.currencyconverter.service.RatesClientErrorException;
import com.example.currencyconverter.service.RatesProvider;
import com.example.currencyconverter.service.RatesProviderException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Override
    public PublishedRates latestRates(String base) {
        try {
            FrankfurterLatest latest = restClient.get()
                    .uri("/latest?base={base}", base)
                    .retrieve()
                    .body(FrankfurterLatest.class);
            return new PublishedRates(latest.base(), latest.date(), latest.rates());
        } catch (HttpClientErrorException e) {
            throw new RatesClientErrorException("Frankfurter answered " + e.getStatusCode(), e);
        } catch (HttpServerErrorException e) {
            throw new RatesProviderException("Frankfurter answered " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new RatesProviderException("Frankfurter could not be reached", e);
        }
    }

    /** Frankfurter's /latest response; {@code date} is the provider's publication date. */
    private record FrankfurterLatest(String base, LocalDate date, Map<String, BigDecimal> rates) {
    }
}
