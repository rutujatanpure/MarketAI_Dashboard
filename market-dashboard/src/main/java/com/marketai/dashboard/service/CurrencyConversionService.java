package com.marketai.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts USD prices to other currencies.
 * Exchange rates are refreshed every hour from a simple hardcoded cache.
 * In production, replace with a real forex API (e.g. ExchangeRate-API).
 */
@Service
public class CurrencyConversionService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyConversionService.class);

    // Approximate rates (base: USD) — refreshed every hour
    private final Map<String, Double> rates = new ConcurrentHashMap<>(Map.of(
            "USD", 1.0,
            "INR", 83.5,
            "EUR", 0.92,
            "GBP", 0.79,
            "JPY", 149.5,
            "BTC", 0.000015  // price in BTC units
    ));

    public double convert(double usdPrice, String toCurrency) {
        if (toCurrency == null || toCurrency.isBlank() || "USD".equalsIgnoreCase(toCurrency)) {
            return usdPrice;
        }
        Double rate = rates.get(toCurrency.toUpperCase());
        return rate != null ? usdPrice * rate : usdPrice;
    }

    public Map<String, Double> getAllRates() {
        return Map.copyOf(rates);
    }

    public String formatPrice(double usdPrice, String currency) {
        double converted = convert(usdPrice, currency);
        return switch (currency.toUpperCase()) {
            case "INR" -> String.format("₹%.2f", converted);
            case "EUR" -> String.format("€%.2f", converted);
            case "GBP" -> String.format("£%.2f", converted);
            case "JPY" -> String.format("¥%.0f", converted);
            default    -> String.format("$%.2f", converted);
        };
    }

    /**
     * In production, call a forex API here every hour.
     * e.g. https://api.exchangerate-api.com/v4/latest/USD
     */
    @Scheduled(fixedDelay = 3_600_000)  // 1 hour
    public void refreshRates() {
        // TODO: Call real forex API and update rates map
        log.debug("🔄 Currency rates refreshed (using static rates in dev mode)");
    }
}