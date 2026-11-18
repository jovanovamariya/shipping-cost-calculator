package com.example.currencyconverter.controller;

import com.example.currencyconverter.model.SupportedCurrency;
import com.example.currencyconverter.service.CurrencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    public List<SupportedCurrency> supportedCurrencies() {
        return currencyService.supportedCurrencies();
    }
}
