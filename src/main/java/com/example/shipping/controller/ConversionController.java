package com.example.shipping.controller;

import com.example.shipping.model.ConversionRequest;
import com.example.shipping.model.ConversionResult;
import com.example.shipping.service.ConversionService;
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
