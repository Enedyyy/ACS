package com.acs.finance.controller;

import com.acs.finance.entity.Budget;
import com.acs.finance.entity.Group;
import com.acs.finance.entity.Transaction;
import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.FinanceService;
import com.acs.finance.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/group")
@Slf4j
public class GroupController extends BaseController {

    private final GroupService groupService;
    private final FinanceService financeService;

    public GroupController(AuthService authService, GroupService groupService, FinanceService financeService) {
        super(authService);
        this.groupService = groupService;
        this.financeService = financeService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestParam(defaultValue = "Группа") String name,
            HttpServletRequest request) {

        User user = requireAuth(request);
        Group group = groupService.create(name);
        groupService.join(user.getId(), group.getId(), 1.0);
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "groupId", group.getId(),
                "name", group.getName()
        ));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(
            @RequestParam String groupId,
        @RequestParam(defaultValue = "1") double share,
        HttpServletRequest request) {
        
        User user = requireAuth(request);
        String normalizedGroupId = normalizeGroupId(groupId);
        if (normalizedGroupId == null || normalizedGroupId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "no_group"));
        }
        Group group = groupService.join(user.getId(), normalizedGroupId, share);
        
        if (group == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "no_group"));
        }
        
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/budget")
    public ResponseEntity<?> budget(HttpServletRequest request) {
        User user = requireAuth(request);
        String groupId = groupService.userGroupId(user.getId());
        
        if (groupId == null) {
            return ResponseEntity.ok(Map.of("items", List.of()));
        }
        
        Map<String, Double> members = groupService.members(groupId);
        Map<String, double[]> agg = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : members.entrySet()) {
            String memberId = entry.getKey();
            double share = entry.getValue();
            
            for (Budget b : financeService.getBudgets(memberId)) {
                double[] arr = agg.computeIfAbsent(b.getCategory(), k -> new double[]{0, 0});
                arr[0] += b.getLimitAsDouble() * share;
                arr[1] += b.getSpentAsDouble() * share;
            }
        }
        
        List<Map<String, Object>> items = agg.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "category", e.getKey(),
                        "limit", FinanceService.round2(e.getValue()[0]),
                        "spent", FinanceService.round2(e.getValue()[1])))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/peers")
    public ResponseEntity<?> peers(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        
        User user = requireAuth(request);
        String groupId = groupService.userGroupId(user.getId());
        
        if (groupId == null) {
            return ResponseEntity.ok(Map.of("items", List.of()));
        }
        
        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
        
        Map<String, Double> members = groupService.members(groupId);
        if (members == null || members.isEmpty()) {
            return ResponseEntity.ok(Map.of("items", List.of()));
        }
        
        List<Map<String, Object>> items = new ArrayList<>();
        
        for (Map.Entry<String, Double> e : members.entrySet()) {
            String memberId = e.getKey();
            if (memberId == null || memberId.trim().isEmpty()) continue;
            
            try {
                List<Transaction> list = financeService.listTransactions(memberId, fromDate, toDate, null);
                double income = 0.0, expense = 0.0;
                
                for (Transaction t : list) {
                    if (t.getAmountAsDouble() > 0) {
                        income += t.getAmountAsDouble();
                    } else {
                        expense += -t.getAmountAsDouble();
                    }
                }
                
                User memberUser = authService.getUserById(memberId);
                String username;
                if (memberUser != null) {
                    username = memberUser.getUsername();
                } else {
                    log.warn("User not found in cache for memberId: {}", memberId);
                    username = memberId.length() > 8 ? memberId.substring(0, 8) + "..." : memberId;
                }
                
                if (username == null || username.isEmpty()) {
                    username = "Пользователь";
                }
                
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("userId", memberId);
                item.put("username", username);
                item.put("income", FinanceService.round2(income));
                item.put("expense", FinanceService.round2(expense));
                items.add(item);
                
            } catch (Exception err) {
                log.warn("Error processing member {}: {}", memberId, err.getMessage());
            }
        }
        
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leave(HttpServletRequest request) {
        User user = requireAuth(request);
        groupService.leave(user.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        User user = requireAuth(request);
        String groupId = groupService.userGroupId(user.getId());
        
        if (groupId == null) {
            return ResponseEntity.ok(Map.of("ok", false));
        }
        
        Double share = groupService.myShare(user.getId());
        String groupName = groupService.getName(groupId);
        if (groupName == null) groupName = "Группа";
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "groupId", groupId,
                "groupName", groupName,
                "share", share != null ? FinanceService.round2(share) : 0.0
        ));
    }

    private String normalizeGroupId(String groupId) {
        if (groupId == null) return null;
        String trimmed = groupId.trim();
        if (trimmed.isEmpty()) return trimmed;
        
        try {
            URI uri = new URI(trimmed);
            String query = uri.getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2 && "join".equals(kv[0]) && !kv[1].isBlank()) {
                        return kv[1];
                    }
                }
            }
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                String candidate = path.substring(path.lastIndexOf('/') + 1);
                if (!candidate.isBlank()) {
                    trimmed = candidate;
                }
            }
        } catch (URISyntaxException ignored) {
            // fall through to other heuristics
        }
        
        int idx = trimmed.indexOf("join=");
        if (idx >= 0 && idx + 5 < trimmed.length()) {
            return trimmed.substring(idx + 5);
        }
        
        int q = trimmed.lastIndexOf('?');
        if (q >= 0 && q + 1 < trimmed.length()) {
            String candidate = trimmed.substring(q + 1);
            if (!candidate.isBlank()) {
                trimmed = candidate;
            }
        }
        
        return trimmed;
    }
}
