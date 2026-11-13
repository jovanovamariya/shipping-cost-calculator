package com.example.shipping.service;

import java.util.Map;

/**
 * Source of exchange-rate data. The conversion logic depends on this interface,
 * never on a concrete provider client, so the provider can be swapped or faked.
 */
public interface RatesProvider {

    /** Supported currencies as code → name, in whatever order the provider returns them. */
    Map<String, String> currencies();
}
