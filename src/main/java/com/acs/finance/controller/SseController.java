package com.acs.finance.controller;

import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.SseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class SseController extends BaseController {

    private final SseService sseService;

    public SseController(AuthService authService, SseService sseService) {
        super(authService);
        this.sseService = sseService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object events(HttpServletRequest request) {
        String sid = getSessionId(request);
        User user = sid != null ? authService.getUserBySession(sid) : null;
        
        if (sid == null || user == null) {
            log.warn("SSE connection rejected: unauthorized");
            return ResponseEntity.status(401)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "unauthorized"));
        }
        
        log.info("SSE connection established: user={}, session={}...", 
                user.getUsername(), sid.substring(0, Math.min(8, sid.length())));
        
        return sseService.register(sid);
    }
}
