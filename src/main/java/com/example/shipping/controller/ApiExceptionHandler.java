package com.example.shipping.controller;

import com.example.shipping.service.InvalidAmountException;
import com.example.shipping.service.RatesProviderException;
import com.example.shipping.service.UnsupportedCurrencyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps service-layer failures to HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String RATES_UNAVAILABLE = "Exchange rates are temporarily unavailable. Please try again later.";

    @ExceptionHandler(RatesProviderException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> ratesUnavailable() {
        return Map.of("message", RATES_UNAVAILABLE);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> unsupportedCurrency(UnsupportedCurrencyException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidAmount(InvalidAmountException e) {
        return Map.of("message", e.getMessage());
    }
}
