package com.acs.finance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String sessionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.debug("SSE connection completed: {}", sessionId);
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.debug("SSE connection timeout: {}", sessionId);
        });
        
        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.debug("SSE connection error: {}", sessionId);
        });
        
        emitters.put(sessionId, emitter);
        
        // Send initial ping
        send(sessionId, "{\"type\":\"hello\"}");
        
        return emitter;
    }

    public void unregister(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    public void send(String sessionId, String json) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return;
        }
        
        try {
            emitter.send(SseEmitter.event().data(json));
        } catch (IOException e) {
            emitters.remove(sessionId);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }

    public void broadcast(Set<String> sessionIds, String json) {
        for (String sid : sessionIds) {
            send(sid, json);
        }
    }

    public Set<String> sessionIds() {
        return new CopyOnWriteArraySet<>(emitters.keySet());
    }
}
