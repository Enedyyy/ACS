package com.acs.finance.service;

import com.acs.finance.model.CurrencyRates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CurrencyService {

    private volatile CurrencyRates cache;
    private volatile long cacheAt;
    private final Object cacheLock = new Object();
    private final ConcurrentHashMap<String, Object[]> histCache = new ConcurrentHashMap<>();
    
    private static final long TTL_LATEST_MS = 60 * 60 * 1000L; // 1 hour
    private static final long TTL_HISTORY_MS = 6 * 60 * 60 * 1000L; // 6 hours

    public CurrencyRates getRates() {
        long now = System.currentTimeMillis();
        
        // Fast path: check cache without locking
        CurrencyRates currentCache = cache;
        if (currentCache != null && (now - cacheAt) < TTL_LATEST_MS) {
            return currentCache;
        }
        
        // Slow path: synchronize cache update
        synchronized (cacheLock) {
            currentCache = cache;
            if (currentCache != null && (now - cacheAt) < TTL_LATEST_MS) {
                return currentCache;
            }
            
            try {
                CurrencyRates r = fetch();
                if (r != null) {
                    cache = r;
                    cacheAt = now;
                    log.info("Currency rates updated successfully: base={}, currencies={}", r.getBase(), r.getRates().size());
                    return r;
                }
            } catch (Exception e) {
                log.error("Failed to fetch currency rates from external API", e);
            }
            
            // Fallback stub
            log.warn("Using fallback currency rates (stub data)");
            Map<String, Double> stub = new HashMap<>();
            stub.put("USD", 0.057);
            stub.put("EUR", 0.053);
            stub.put("RUB", 5.2);
            CurrencyRates fallback = new CurrencyRates("MDL", stub, now);
            cache = fallback;
            cacheAt = now;
            return fallback;
        }
    }

    public double convert(String from, String to, double amount, String date) {
        if (from == null || to == null) return Double.NaN;
        from = from.toUpperCase();
        to = to.toUpperCase();
        
        Map<String, Double> mdlMap = new HashMap<>(getRates().getRates());
        mdlMap.put("MDL", 1.0);
        
        if (date != null && !date.isBlank()) {
            Map<String, Double> histRates = historyOnDate("MDL", List.of(from, to), date);
            if (histRates != null && histRates.containsKey(from) && histRates.containsKey(to)) {
                mdlMap = new HashMap<>(histRates);
                mdlMap.put("MDL", 1.0);
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

    public Map<String, Double> historyOnDate(String base, List<String> symbols, String date) {
        try {
            String sym = String.join(",", symbols);
            String url = "https://api.exchangerate.host/" + date + "?base=" + encode(base) + "&symbols=" + encode(sym);
            String key = "d:" + url;
            
            Object[] cached = histCache.get(key);
            long now = System.currentTimeMillis();
            if (cached != null && now - (long) cached[0] < TTL_HISTORY_MS) {
                @SuppressWarnings("unchecked")
                Map<String, Double> m = (Map<String, Double>) cached[1];
                return m;
            }
            
            Map<String, Double> map = fetchRatesObject(url);
            if (map != null) {
                histCache.put(key, new Object[]{now, map});
            } else {
                log.warn("Failed to fetch currency history: date={}", date);
            }
            return map;
        } catch (Exception e) {
            log.error("Error fetching currency history: date={}", date, e);
            return null;
        }
    }

    private CurrencyRates fetch() {
        try {
            String url = "https://api.exchangerate.host/latest?base=MDL&symbols=USD,EUR,RUB";
            Map<String, Double> map = fetchRatesObject(url);
            if (map != null) {
                return new CurrencyRates("MDL", map, System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.error("Failed to fetch currency rates", e);
        }
        return null;
    }

    private Map<String, Double> fetchRatesObject(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() != 200) {
                return null;
            }
            
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            return parseRatesFromJson(sb.toString());
        } catch (Exception e) {
            log.error("HTTP request failed: {}", urlStr, e);
            return null;
        }
    }

    private Map<String, Double> parseRatesFromJson(String json) {
        // Simple JSON parsing without external dependencies
        Map<String, Double> result = new HashMap<>();
        try {
            int ratesStart = json.indexOf("\"rates\"");
            if (ratesStart < 0) return null;
            
            int braceStart = json.indexOf("{", ratesStart);
            int braceEnd = json.indexOf("}", braceStart);
            if (braceStart < 0 || braceEnd < 0) return null;
            
            String ratesStr = json.substring(braceStart + 1, braceEnd);
            String[] pairs = ratesStr.split(",");
            
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    double value = Double.parseDouble(kv[1].trim());
                    result.put(key, value);
                }
            }
            
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.error("Failed to parse currency rates JSON", e);
            return null;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
