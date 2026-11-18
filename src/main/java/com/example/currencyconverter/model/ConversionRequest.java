package com.example.currencyconverter.model;

import java.math.BigDecimal;

public record ConversionRequest(BigDecimal amount, String from, String to) {
}
