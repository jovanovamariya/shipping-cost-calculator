package com.example.shipping.service;

import com.example.shipping.model.ConversionResult;
import com.example.shipping.model.PublishedRates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.shipping.service.FakeRatesProviders.currenciesFrom;
import static com.example.shipping.service.FakeRatesProviders.ratesFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionServiceTest {

    /** Builds a CurrencyService that knows exactly the given currency codes. */
    private static CurrencyService currenciesOf(String... codes) {
        Map<String, String> codeMap = Arrays.stream(codes)
                .collect(Collectors.toMap(c -> c, c -> c));
        return new CurrencyService(currenciesFrom(() -> codeMap), Clock.systemUTC(), Duration.ofDays(1));
    }

    @Test
    @DisplayName("The one where 100 EUR → USD at 1.0850 (published 2026-11-11) gives 108.50 USD, rate 1.0850, rate date 2026-11-11")
    void convertsAtPublishedRateAndReportsPublicationDate() {
        RatesProvider fakeProvider = ratesFrom(base -> new PublishedRates(
                base, LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850"))));

        ConversionResult conversion = new ConversionService(fakeProvider, currenciesOf("EUR", "USD"))
                .convert(new BigDecimal("100"), "EUR", "USD");

        assertThat(conversion.result()).isEqualByComparingTo("108.50");
        assertThat(conversion.rate()).isEqualByComparingTo("1.0850");
        assertThat(conversion.rateDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(conversion.stale()).isFalse();
    }

    @Test
    @DisplayName("The one where 100 XYZ → USD is rejected with 'Unsupported currency: XYZ'")
    void rejectsUnsupportedFromCurrency() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "XYZ", "USD"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where 100 EUR → XYZ is rejected with 'Unsupported currency: XYZ'")
    void rejectsUnsupportedToCurrency() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "EUR", "XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where lower-case 'eur' and 'usd' are normalised to upper case before validation")
    void normalisesCodesBeforeValidating() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates(
                        base, LocalDate.of(2026, 11, 11), Map.of("USD", new BigDecimal("1.0850")))),
                currenciesOf("EUR", "USD"));

        ConversionResult result = service.convert(new BigDecimal("100"), "eur", "usd");

        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("USD");
    }

    @Test
    @DisplayName("The one where XYZ → XYZ is still rejected — unsupported code is checked before the same-currency short-circuit")
    void unsupportedSameCurrencyIsRejected() {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal("100"), "XYZ", "XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency: XYZ");
    }

    @Test
    @DisplayName("The one where 100 EUR → EUR gives 100.00, rate 1, rate date today, and no provider call is made")
    void sameCurrencyConvertsAtRateOneWithNoProviderCall() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        Clock fixed = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        // If the same-currency branch is absent, the provider is called and returns a wrong rate (0.5),
        // producing 50.00 instead of 100.00 and exposing the missing branch as an assertion failure.
        ConversionService service = new ConversionService(
                ratesFrom(base -> new PublishedRates(base, LocalDate.of(2020, 1, 1),
                        Map.of("EUR", new BigDecimal("0.5")))),
                currenciesOf("EUR", "USD"),
                fixed);

        ConversionResult result = service.convert(new BigDecimal("100"), "EUR", "EUR");

        assertThat(result.result()).isEqualByComparingTo("100.00");
        assertThat(result.rate()).isEqualByComparingTo("1");
        assertThat(result.rateDate()).isEqualTo(today);
        assertThat(result.stale()).isFalse();
    }

    @ParameterizedTest(name = "The one where amount {0} is rejected as not greater than zero")
    @CsvSource({
            "0",
            "-5",
    })
    void rejectsAmountNotGreaterThanZero(String amount) {
        ConversionService service = new ConversionService(
                ratesFrom(base -> { throw new UnsupportedOperationException("should not reach provider"); }),
                currenciesOf("EUR", "USD"));

        assertThatThrownBy(() -> service.convert(new BigDecimal(amount), "EUR", "USD"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }
}
