package com.acs.finance.controller;

import com.acs.finance.entity.Reminder;
import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.FinanceService;
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
public class ReminderController extends BaseController {

    private final FinanceService financeService;

    public ReminderController(AuthService authService, FinanceService financeService) {
        super(authService);
        this.financeService = financeService;
    }

    @PostMapping("/reminder/add")
    public ResponseEntity<?> add(
            @RequestParam String dueDate,
            @RequestParam(defaultValue = "Платёж") String message,
            @RequestParam(required = false) Double amount,
            HttpServletRequest request) {
        
        User user = requireAuth(request);
        LocalDate due = LocalDate.parse(dueDate);
        
        financeService.addReminder(user.getId(), due, message, amount);
        
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/reminders")
    public ResponseEntity<?> list(HttpServletRequest request) {
        User user = requireAuth(request);
        List<Reminder> reminders = financeService.getReminders(user.getId());
        
        List<Map<String, Object>> items = reminders.stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", r.getId());
                    item.put("date", LocalDate.ofEpochDay(r.getDueEpochDay()).toString());
                    item.put("message", r.getMessage());
                    item.put("amount", r.getAmountAsDouble() != null
                            ? FinanceService.round2(r.getAmountAsDouble()) : null);
                    return item;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of("items", items));
    }
}
