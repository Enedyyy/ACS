package com.acs.finance.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoCategorizer {
	private final Map<String, String> keywordToCategory = new ConcurrentHashMap<>();

	public AutoCategorizer() {
		// simple defaults (ru/ro/en keywords)
		addRule("еда", "Питание"); addRule("ресторан", "Питание"); addRule("кафе", "Питание");
		addRule("market", "Питание"); addRule("supermarket", "Питание"); addRule("alimentara", "Питание");
		addRule("такси", "Транспорт"); addRule("transport", "Транспорт"); addRule("bus", "Транспорт");
		addRule("аренда", "Жильё"); addRule("квартира", "Жильё"); addRule("rent", "Жильё");
		addRule("зарплата", "Доход"); addRule("salary", "Доход");
		addRule("аптека", "Здоровье"); addRule("pharmacy", "Здоровье");
	}

	public void addRule(String keyword, String category) {
		if (keyword == null || keyword.isBlank() || category == null || category.isBlank()) return;
		keywordToCategory.put(keyword.toLowerCase(Locale.ROOT).trim(), category.trim());
	}

	public String categorize(String description) {
		if (description == null || description.isBlank()) return null;
		String d = description.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> e : keywordToCategory.entrySet()) {
			if (d.contains(e.getKey())) return e.getValue();
		}
		return null;
	}

	public Map<String,String> rules() {
		return new LinkedHashMap<>(keywordToCategory);
	}
}


