package com.acs.finance.controller;

import com.acs.finance.entity.Budget;
import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.FinanceService;
import com.acs.finance.service.SseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
public class BudgetController extends BaseController {

    private final FinanceService financeService;
    private final SseService sseService;

    public BudgetController(AuthService authService, FinanceService financeService, SseService sseService) {
        super(authService);
        this.financeService = financeService;
        this.sseService = sseService;
    }

    @PostMapping("/budget/set")
    public ResponseEntity<?> set(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") double limit,
            HttpServletRequest request) {
        
        User user = requireAuth(request);
        Budget budget = financeService.setBudget(user.getId(), category, limit);
        
        sseService.send(getSessionId(request), "{\"type\":\"budget-update\"}");
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "category", category,
                "limit", budget.getLimitAsDouble()
        ));
    }

    @GetMapping("/budget")
    public ResponseEntity<?> get(HttpServletRequest request) {
        User user = requireAuth(request);
        List<Budget> budgets = financeService.getBudgets(user.getId());
        
        List<Map<String, Object>> items = budgets.stream()
                .map(b -> Map.<String, Object>of(
                        "category", b.getCategory(),
                        "limit", FinanceService.round2(b.getLimitAsDouble()),
                        "spent", FinanceService.round2(b.getSpentAsDouble())))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/budget/delete")
    public ResponseEntity<?> delete(@RequestParam String category, HttpServletRequest request) {
        User user = requireAuth(request);
        
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "no_category"));
        }
        
        financeService.deleteBudget(user.getId(), category);
        sseService.send(getSessionId(request), "{\"type\":\"budget-update\"}");
        
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
