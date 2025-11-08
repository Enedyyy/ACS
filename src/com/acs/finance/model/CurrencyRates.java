package com.acs.finance.model;

import java.util.Map;

public class CurrencyRates {
	public final String base; // MDL
	public final Map<String, Double> rates; // USD, EUR, RUB
	public final long timestamp;

	public CurrencyRates(String base, Map<String, Double> rates, long timestamp) {
		this.base = base;
		this.rates = rates;
		this.timestamp = timestamp;
	}
}


