package com.acs.finance.controller;

import com.acs.finance.entity.Budget;
import com.acs.finance.entity.Transaction;
import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.AutoCategorizerService;
import com.acs.finance.service.FinanceService;
import com.acs.finance.service.SseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
public class TransactionController extends BaseController {

    private final FinanceService financeService;
    private final SseService sseService;
    private final AutoCategorizerService categorizer;

    public TransactionController(AuthService authService, FinanceService financeService,
                                  SseService sseService, AutoCategorizerService categorizer) {
        super(authService);
        this.financeService = financeService;
        this.sseService = sseService;
        this.categorizer = categorizer;
    }

    @PostMapping("/transaction/add")
    public ResponseEntity<?> add(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") double amount,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        
        User user = requireAuth(request);
        LocalDate localDate = LocalDate.parse(date);
        
        String cat = (category != null && !category.isBlank()) ? category : null;
        String desc = (description != null && !description.isBlank()) ? description : null;
        
        if (cat == null) {
            cat = categorizer.categorize(desc);
        }
        
        Transaction tx = financeService.addTransaction(user.getId(), localDate, cat, desc, amount);
        log.info("Transaction added: user={}, amount={}, category={}, date={}", 
                user.getUsername(), amount, cat, date);
        
        String sid = getSessionId(request);
        sseService.send(sid, "{\"type\":\"tx-added\"}");
        sseService.send(sid, "{\"type\":\"budget-update\"}");
        
        // Alert: budget exceed
        if (cat != null) {
            for (Budget b : financeService.getBudgets(user.getId())) {
                if (b.getCategory().equalsIgnoreCase(cat) && b.getLimitAsDouble() > 0 
                        && b.getSpentAsDouble() > b.getLimitAsDouble()) {
                    log.warn("Budget exceeded: user={}, category={}, limit={}, spent={}", 
                            user.getUsername(), cat, b.getLimitAsDouble(), b.getSpentAsDouble());
                    sseService.send(sid, "{\"type\":\"alert\",\"message\":\"Превышен бюджет по категории '" 
                            + escapeJson(cat) + "'\"}");
                    break;
                }
            }
        }
        
        return ResponseEntity.ok(Map.of("ok", true, "id", tx.getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            HttpServletRequest request) {
        
        User user = requireAuth(request);
        
        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
        String cat = (category != null && !category.isBlank()) ? category : null;
        
        List<Transaction> transactions = financeService.listTransactions(user.getId(), fromDate, toDate, cat);
        
        List<Map<String, Object>> items = transactions.stream()
                .map(tx -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", tx.getId());
                    item.put("date", LocalDate.ofEpochDay(tx.getDateEpochDay()).toString());
                    item.put("category", tx.getCategory());
                    item.put("description", tx.getDescription());
                    item.put("amount", FinanceService.round2(tx.getAmountAsDouble()));
                    return item;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/transaction/delete")
    public ResponseEntity<?> delete(@RequestParam String id, HttpServletRequest request) {
        User user = requireAuth(request);
        
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "no_id"));
        }
        
        boolean ok = financeService.deleteTransaction(user.getId(), id);
        if (ok) {
            sseService.send(getSessionId(request), "{\"type\":\"budget-update\"}");
            return ResponseEntity.ok(Map.of("ok", true));
        } else {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "not_found"));
        }
    }
}
