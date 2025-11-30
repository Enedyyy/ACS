package com.acs.finance.controller;

import com.acs.finance.service.AuthService;
import com.acs.finance.service.AutoCategorizerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class CategorizerController extends BaseController {

    private final AutoCategorizerService categorizer;

    public CategorizerController(AuthService authService, AutoCategorizerService categorizer) {
        super(authService);
        this.categorizer = categorizer;
    }

    @GetMapping("/categorizer/suggest")
    public ResponseEntity<?> suggest(
            @RequestParam(defaultValue = "") String desc,
            HttpServletRequest request) {
        
        requireAuth(request);
        String category = categorizer.categorize(desc);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("category", category); // null will be serialized as JSON null
        return ResponseEntity.ok(response);
    }
}
