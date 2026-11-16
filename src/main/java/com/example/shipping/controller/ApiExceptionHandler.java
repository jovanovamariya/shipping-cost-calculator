package com.example.shipping.controller;

import com.example.shipping.service.InvalidAmountException;
import com.example.shipping.service.RatesProviderException;
import com.example.shipping.service.UnsupportedCurrencyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/** Maps service-layer failures to HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String RATES_UNAVAILABLE = "Exchange rates are temporarily unavailable. Please try again later.";

    @ExceptionHandler(RatesProviderException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, Object> ratesUnavailable(HttpServletRequest request) {
        return errorBody(HttpStatus.SERVICE_UNAVAILABLE, RATES_UNAVAILABLE, request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> unsupportedCurrency(UnsupportedCurrencyException e, HttpServletRequest request) {
        return errorBody(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> invalidAmount(InvalidAmountException e, HttpServletRequest request) {
        return errorBody(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, Object> unexpectedError(HttpServletRequest request) {
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private static Map<String, Object> errorBody(HttpStatus status, String message, HttpServletRequest request) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message,
                "path",      request.getRequestURI()
        );
    }
}
