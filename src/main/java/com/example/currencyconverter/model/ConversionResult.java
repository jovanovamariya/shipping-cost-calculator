package com.example.currencyconverter.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionResult(BigDecimal amount, String from, String to,
                               BigDecimal result, BigDecimal rate, LocalDate rateDate, boolean stale) {
}
