package com.example.currencyconverter.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Rates for one base currency as the provider published them; the publication date is the provider's, not ours. */
public record PublishedRates(String base, LocalDate publicationDate, Map<String, BigDecimal> rates) {
}
