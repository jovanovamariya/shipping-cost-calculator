package com.example.shipping.model;

import java.math.BigDecimal;

public record ConversionRequest(BigDecimal amount, String from, String to) {
}
