package com.example.shipping.config;

import com.example.shipping.client.FrankfurterRatesProvider;
import com.example.shipping.service.ConversionService;
import com.example.shipping.service.CurrencyService;
import com.example.shipping.service.RatesProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Configuration
public class RatesConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RatesProvider ratesProvider(RestClient.Builder restClientBuilder,
                                @Value("${rates.provider.base-url}") String baseUrl) {
        return new FrankfurterRatesProvider(restClientBuilder, baseUrl);
    }

    @Bean
    CurrencyService currencyService(RatesProvider ratesProvider, Clock clock,
                                    @Value("${currencies.list-ttl}") Duration listTtl) {
        return new CurrencyService(ratesProvider, clock, listTtl);
    }

    @Bean
    ConversionService conversionService(RatesProvider ratesProvider, CurrencyService currencyService, Clock clock,
                                        @Value("${rates.max-stale-age-days:-1}") int maxStaleAgeConfig) {
        Optional<Integer> maxStaleAge = maxStaleAgeConfig < 0 ? Optional.empty() : Optional.of(maxStaleAgeConfig);
        return new ConversionService(ratesProvider, currencyService, clock, maxStaleAge);
    }
}
