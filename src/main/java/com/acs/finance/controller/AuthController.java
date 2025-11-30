package com.acs.finance.controller;

import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class AuthController extends BaseController {

    public AuthController(AuthService authService) {
        super(authService);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam String username, @RequestParam String password) {
        User user = authService.register(username, password);
        if (user == null) {
            log.warn("Registration failed for username: {}", username);
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "user_exists_or_bad"));
        }
        log.info("User registered: {} (id: {})", username, user.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response) {
        
        User user = authService.login(username, password);
        if (user == null) {
            log.warn("Login failed for username: {}", username);
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "bad_credentials"));
        }
        
        String sid = authService.createSession(user.getUsername());
        
        Cookie cookie = new Cookie("SID", sid);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        
        log.info("User logged in: {} (id: {})", username, user.getId());
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername()
                )
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String sid = getSessionId(request);
        User user = getUser(request);
        
        authService.destroySession(sid);
        
        Cookie cookie = new Cookie("SID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
        if (user != null) {
            log.info("User logged out: {}", user.getUsername());
        }
        
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        User user = getUser(request);
        if (user == null) {
            return ResponseEntity.ok(Map.of("ok", false));
        }
        
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername()
                )
        ));
    }
}
