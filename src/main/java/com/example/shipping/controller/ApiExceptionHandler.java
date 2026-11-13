package com.example.shipping.controller;

import com.example.shipping.service.RatesProviderException;
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
}
