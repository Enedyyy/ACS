package com.acs.finance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Map;

@Getter
@AllArgsConstructor
public class CurrencyRates {
    private final String base;
    private final Map<String, Double> rates;
    private final long timestamp;
}
