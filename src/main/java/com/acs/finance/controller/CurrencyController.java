package com.acs.finance.controller;

import com.acs.finance.model.CurrencyRates;
import com.acs.finance.service.CurrencyService;
import com.acs.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping("/currency")
    public ResponseEntity<?> rates(
            @RequestParam(defaultValue = "MDL") String base,
            @RequestParam(required = false) String symbols) {
        
        CurrencyRates rates = currencyService.getRates();
        
        Map<String, Double> mdlMap = new HashMap<>(rates.getRates());
        mdlMap.put("MDL", 1.0);
        
        List<String> symbolList = (symbols == null || symbols.isBlank()) 
                ? List.of("USD", "EUR", "RUB", "MDL")
                : Arrays.asList(symbols.split(","));
        
        Map<String, Double> result = new LinkedHashMap<>();
        for (String sym : symbolList) {
            if (!mdlMap.containsKey(sym)) continue;
            
            double rate;
            if (base.equals("MDL")) {
                rate = mdlMap.get(sym);
            } else {
                Double b = mdlMap.get(base);
                if (b == null || b == 0.0) continue;
                rate = mdlMap.get(sym) / b;
            }
            result.put(sym, FinanceService.round2(rate));
        }
        
        return ResponseEntity.ok(Map.of("base", base, "rates", result));
    }

    @GetMapping("/currency/convert")
    public ResponseEntity<?> convert(
            @RequestParam(defaultValue = "USD") String from,
            @RequestParam(defaultValue = "MDL") String to,
            @RequestParam(defaultValue = "0") double amount,
            @RequestParam(required = false) String date) {
        
        double result = currencyService.convert(from, to, amount, date);
        
        if (Double.isNaN(result)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "bad_currency"));
        }
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "from", from.toUpperCase(),
                "to", to.toUpperCase(),
                "amount", FinanceService.round2(amount),
                "result", FinanceService.round2(result)
        ));
    }
}
