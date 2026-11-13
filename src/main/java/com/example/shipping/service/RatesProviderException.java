package com.example.shipping.service;

/** Thrown by a {@link RatesProvider} when the provider cannot deliver data (server error, timeout, connection failure). */
public class RatesProviderException extends RuntimeException {

    public RatesProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
