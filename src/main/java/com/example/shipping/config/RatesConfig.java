package com.example.shipping.config;

import com.example.shipping.client.FrankfurterRatesProvider;
import com.example.shipping.service.CurrencyService;
import com.example.shipping.service.RatesProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RatesConfig {

    @Bean
    RatesProvider ratesProvider(RestClient.Builder restClientBuilder,
                                @Value("${rates.provider.base-url}") String baseUrl) {
        return new FrankfurterRatesProvider(restClientBuilder, baseUrl);
    }

    @Bean
    CurrencyService currencyService(RatesProvider ratesProvider) {
        return new CurrencyService(ratesProvider);
    }
}
