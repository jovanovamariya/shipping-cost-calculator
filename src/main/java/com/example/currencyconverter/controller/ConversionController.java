package com.example.currencyconverter.controller;

import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.model.ConversionResult;
import com.example.currencyconverter.service.ConversionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convert")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping
    public ConversionResult convert(@RequestBody ConversionRequest request) {
        return conversionService.convert(request.amount(), request.from(), request.to());
    }
}
