package com.example.currencyconverter.service;

/**
 * Thrown by a {@link RatesProvider} when the provider returns a 4xx client error.
 * These are not counted as circuit-breaker failures — a 4xx is a protocol-level client
 * error, not evidence that the provider is down.
 */
public class RatesClientErrorException extends RatesProviderException {

    public RatesClientErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
