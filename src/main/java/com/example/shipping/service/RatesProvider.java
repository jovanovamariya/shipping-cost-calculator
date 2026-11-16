package com.example.shipping.service;

import com.example.shipping.model.PublishedRates;

import java.util.Map;

/**
 * Source of exchange-rate data. The conversion logic depends on this interface,
 * never on a concrete provider client, so the provider can be swapped or faked.
 */
public interface RatesProvider {

    /** Supported currencies as code → name, in whatever order the provider returns them. */
    Map<String, String> currencies();

    /** The latest rates for the base currency, with the date the provider published them. */
    PublishedRates latestRates(String base);
}
