package com.example.currencyconverter.service;

public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String message) {
        super(message);
    }
}
