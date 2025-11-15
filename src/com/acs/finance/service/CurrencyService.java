package com.acs.finance.service;

import com.acs.finance.model.CurrencyRates;
import com.acs.finance.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CurrencyService {
	private volatile CurrencyRates cache;
	private volatile long cacheAt;
	private final java.util.concurrent.ConcurrentHashMap<String, Object[]> histCache = new java.util.concurrent.ConcurrentHashMap<>(); // key -> [at(ms), Map]
	private static final long TTL_LATEST_MS = 60 * 60 * 1000L; // 1h
	private static final long TTL_HISTORY_MS = 6 * 60 * 60 * 1000L; // 6h

	public CurrencyRates getRates() {
		long now = System.currentTimeMillis();
		if (cache != null && (now - cacheAt) < TTL_LATEST_MS) {
			Logger.trace("Currency rates returned from cache");
			return cache;
		}
		try {
			Logger.debug("Fetching currency rates from external API");
			CurrencyRates r = fetch();
			if (r != null) {
				cache = r;
				cacheAt = now;
				Logger.info("Currency rates updated successfully: base=%s, currencies=%d", r.base, r.rates.size());
				return r;
			}
		} catch (Exception e) {
			Logger.error("Failed to fetch currency rates from external API", e);
		}
		// fallback stub
		Logger.warning("Using fallback currency rates (stub data)");
		Map<String, Double> stub = new HashMap<>();
		stub.put("USD", 0.057); // ~ 1 MDL = 0.057 USD (example)
		stub.put("EUR", 0.053);
		stub.put("RUB", 5.2);
		cache = new CurrencyRates("MDL", stub, now);
		cacheAt = now;
		return cache;
	}

	public double convert(String from, String to, double amount, String date) {
		if (from == null || to == null) return Double.NaN;
		from = from.toUpperCase(); to = to.toUpperCase();
		Map<String, Double> mdlMap = new HashMap<>(getRates().rates);
		mdlMap.put("MDL", 1.0);
		if (date != null && !date.isBlank()) {
			// use historical rates for date
			Map<String, Double> one = historyOnDate("MDL", java.util.List.of(from, to), date);
			if (one != null && one.containsKey(from) && one.containsKey(to)) {
				mdlMap = new HashMap<>(one); mdlMap.put("MDL", 1.0);
			}
		}
		Double rf = mdlMap.get(from);
		Double rt = mdlMap.get(to);
		if (rf == null || rt == null || rf == 0.0) return Double.NaN;
		double rate;
		if (from.equals("MDL")) rate = rt;
		else if (to.equals("MDL")) rate = 1.0 / rf;
		else rate = rt / rf;
		return amount * rate;
	}

	public Map<String, Double> historyOnDate(String base, java.util.List<String> symbols, String date) {
		try {
			String sym = String.join(",", symbols);
			String url = "https://api.exchangerate.host/"+date+"?base="+encode(base)+"&symbols="+encode(sym);
			String key = "d:"+url;
			Object[] cached = histCache.get(key);
			long now = System.currentTimeMillis();
			if (cached != null && now - (long)cached[0] < TTL_HISTORY_MS) {
				Logger.trace("Currency history returned from cache: date=%s", date);
				@SuppressWarnings("unchecked") Map<String, Double> m = (Map<String, Double>) cached[1];
				return m;
			}
			Logger.debug("Fetching currency history from external API: date=%s, base=%s", date, base);
			Map<String, Double> map = fetchRatesObject(url);
			if (map != null) {
				histCache.put(key, new Object[]{now, map});
				Logger.debug("Currency history fetched successfully: date=%s", date);
			} else {
				Logger.warning("Failed to fetch currency history: date=%s", date);
			}
			return map;
		} catch (Exception e) {
			Logger.error("Error fetching currency history: date=%s", e, date);
			return null;
		}
	}

	public Map<String, Map<String, Double>> historyRange(String base, String symbol, String from, String to) {
		try {
			String url = "https://api.exchangerate.host/timeseries?start_date="+encode(from)+"&end_date="+encode(to)+"&base="+encode(base)+"&symbols="+encode(symbol);
			String key = "r:"+url;
			Object[] cached = histCache.get(key);
			long now = System.currentTimeMillis();
			if (cached != null && now - (long)cached[0] < TTL_HISTORY_MS) {
				Logger.trace("Currency history range returned from cache: from=%s, to=%s", from, to);
				@SuppressWarnings("unchecked") Map<String, Map<String, Double>> m = (Map<String, Map<String, Double>>) cached[1];
				return m;
			}
			Logger.debug("Fetching currency history range from external API: from=%s, to=%s, base=%s", from, to, base);
			// fetch
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
			c.setConnectTimeout(4000);
			c.setReadTimeout(4000);
			c.setRequestMethod("GET");
			if (c.getResponseCode() != 200) {
				Logger.warning("Currency API returned non-200 status: %d, trying fallback", c.getResponseCode());
				// try frankfurter if supported
				if (supportsFrankfurter(base) && supportsFrankfurter(symbol)) {
					Logger.debug("Trying Frankfurter API as fallback");
					var ff = frankfurterRange(base, symbol, from, to);
					if (ff != null) {
						histCache.put(key, new Object[]{now, ff});
						Logger.info("Currency history range fetched from Frankfurter API");
						return ff;
					}
				}
				// fallback: day-by-day fetch
				Logger.debug("Using day-by-day fallback for currency history");
				return fetchByDaysFallback(base, symbol, from, to, now, key);
			}
			try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line);
				String json = sb.toString();
				int i = json.indexOf("\"rates\""); if (i < 0) return null;
				int start = json.indexOf('{', i); int end = json.lastIndexOf('}');
				if (start < 0 || end < 0) return null;
				String ratesObj = json.substring(start+1, end-1);
				Map<String, Map<String, Double>> out = new java.util.LinkedHashMap<>();
				for (String dayPart : splitTopLevel(ratesObj)) {
					int colon = dayPart.indexOf(':');
					if (colon < 0) continue;
					String date = dayPart.substring(0, colon).replace("\""," ").trim();
					String obj = dayPart.substring(colon+1).trim();
					int s = obj.indexOf('{'); int e = obj.indexOf('}', s);
					if (s<0||e<0) continue;
					String inner = obj.substring(s+1, e);
					Map<String, Double> map = new HashMap<>();
					for (String kvp : inner.split(",")) {
						String[] kv = kvp.split(":");
						if (kv.length==2) {
							String k = kv[0].replace("\""," ").trim();
							double v = Double.parseDouble(kv[1]);
							map.put(k, v);
						}
					}
					out.put(date, map);
				}
				histCache.put(key, new Object[]{now, out});
				return out;
			}
		} catch (Exception ignored) { return null; }
	}

	private Map<String, Map<String, Double>> fetchByDaysFallback(String base, String symbol, String from, String to, long now, String cacheKey) {
		try {
			java.time.LocalDate start = java.time.LocalDate.parse(from);
			java.time.LocalDate end = java.time.LocalDate.parse(to);
			if (end.isAfter(java.time.LocalDate.now())) end = java.time.LocalDate.now();
			if (start.isAfter(end)) { var tmp = start; start = end; end = tmp; }
			Map<String, Map<String, Double>> out = new java.util.LinkedHashMap<>();
			for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
				Map<String, Double> m = historyOnDate(base, java.util.List.of(symbol), d.toString());
				if (m != null && m.containsKey(symbol)) {
					Map<String, Double> only = new HashMap<>(); only.put(symbol, m.get(symbol));
					out.put(d.toString(), only);
				}
			}
			if (!out.isEmpty()) histCache.put(cacheKey, new Object[]{now, out});
			return out.isEmpty()? null : out;
		} catch (Exception e) {
			return null;
		}
	}

	// Frankfurter.app fallback (supports ограниченный список валют, без MDL)
	private static final java.util.Set<String> FRANKFURTER = java.util.Set.of(
		"EUR","USD","GBP","CHF","JPY","AUD","CAD","NZD","SEK","NOK","DKK","PLN","CZK","HUF","RON","BGN","TRY","CNY","HKD","SGD","INR","MXN","ZAR","ILS","KRW","BRL","PHP","THB","MYR","IDR"
	);
	private static boolean supportsFrankfurter(String code) { return FRANKFURTER.contains(code.toUpperCase()); }

	private Map<String, Map<String, Double>> frankfurterRange(String base, String symbol, String from, String to) {
		try {
			// API: https://api.frankfurter.app/2020-01-01..2020-01-31?from=USD&to=EUR
			String url = "https://api.frankfurter.app/"+from+".."+to+"?from="+encode(base)+"&to="+encode(symbol);
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
			c.setConnectTimeout(4000); c.setReadTimeout(4000); c.setRequestMethod("GET");
			if (c.getResponseCode()!=200) return null;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder sb = new StringBuilder(); String line; while ((line=br.readLine())!=null) sb.append(line);
				String json = sb.toString();
				int i = json.indexOf("\"rates\""); if (i < 0) return null;
				int start = json.indexOf('{', i); int end = json.lastIndexOf('}');
				if (start < 0 || end < 0) return null;
				String ratesObj = json.substring(start+1, end-1);
				Map<String, Map<String, Double>> out = new java.util.LinkedHashMap<>();
				for (String dayPart : splitTopLevel(ratesObj)) {
					int colon = dayPart.indexOf(':');
					if (colon < 0) continue;
					String date = dayPart.substring(0, colon).replace("\""," ").trim();
					String obj = dayPart.substring(colon+1).trim();
					int s = obj.indexOf('{'); int e = obj.indexOf('}', s);
					if (s<0||e<0) continue;
					String inner = obj.substring(s+1, e);
					Map<String, Double> map = new HashMap<>();
					for (String kvp : inner.split(",")) {
						String[] kv = kvp.split(":");
						if (kv.length==2) {
							String k = kv[0].replace("\""," ").trim();
							double v = Double.parseDouble(kv[1]);
							map.put(k, v);
						}
					}
					out.put(date, map);
				}
				return out;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String encode(String s) throws Exception { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }

	private Map<String, Double> fetchRatesObject(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(4000); c.setReadTimeout(4000); c.setRequestMethod("GET");
		if (c.getResponseCode() != 200) return null;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line);
			String json = sb.toString();
			int i = json.indexOf("\"rates\""); if (i < 0) return null;
			int start = json.indexOf('{', i); int end = json.indexOf('}', start);
			if (start < 0 || end < 0) return null;
			String ratesObj = json.substring(start+1, end);
			Map<String, Double> map = new HashMap<>();
			for (String part : ratesObj.split(",")) {
				String[] kv = part.split(":");
				if (kv.length == 2) {
					String k = kv[0].replace("\""," ").trim();
					double v = Double.parseDouble(kv[1]);
					map.put(k, v);
				}
			}
			return map;
		}
	}

	private static java.util.List<String> splitTopLevel(String s) {
		java.util.List<String> list = new java.util.ArrayList<>();
		int depth=0; int last=0;
		for (int i=0;i<s.length();i++) {
			char ch = s.charAt(i);
			if (ch=='{') depth++;
			else if (ch=='}') depth--;
			else if (ch==',' && depth==0) { list.add(s.substring(last, i)); last = i+1; }
		}
		if (last < s.length()) list.add(s.substring(last));
		return list;
	}

	private CurrencyRates fetch() throws Exception {
		// Use exchangerate.host free API
		URL url = new URL("https://api.exchangerate.host/latest?base=MDL&symbols=USD,EUR,RUB");
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setConnectTimeout(4000);
		c.setReadTimeout(4000);
		c.setRequestMethod("GET");
		int code = c.getResponseCode();
		if (code != 200) return null;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line; while ((line = br.readLine()) != null) sb.append(line);
			String json = sb.toString();
			// naive parsing for rates object
			int i = json.indexOf("\"rates\""); if (i < 0) return null;
			int start = json.indexOf('{', i); int end = json.indexOf('}', start);
			if (start < 0 || end < 0) return null;
			String ratesObj = json.substring(start+1, end);
			Map<String, Double> map = new HashMap<>();
			for (String part : ratesObj.split(",")) {
				String[] kv = part.split(":");
				if (kv.length == 2) {
					String k = kv[0].replace("\""," ").trim();
					double v = Double.parseDouble(kv[1]);
					map.put(k, v);
				}
			}
			return new CurrencyRates("MDL", map, System.currentTimeMillis());
		}
	}
}


