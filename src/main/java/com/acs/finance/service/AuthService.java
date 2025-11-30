package com.acs.finance.service;

import com.acs.finance.entity.Session;
import com.acs.finance.entity.User;
import com.acs.finance.repository.SessionRepository;
import com.acs.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    
    // In-memory cache for quick session lookups
    private final Map<String, User> userCache = new ConcurrentHashMap<>();
    private final Map<String, String> sessionCache = new ConcurrentHashMap<>(); // sid -> username

    @Transactional
    public User register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Registration attempt with empty username or password");
            return null;
        }
        
        if (userRepository.existsByUsername(username)) {
            log.warn("Registration failed: username already exists: {}", username);
            return null;
        }
        
        try {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(hashPassword(password));
            user.setShare(1.0);
            
            user = userRepository.save(user);
            userCache.put(username, user);
            
            log.info("User successfully registered: {}", username);
            return user;
        } catch (Exception e) {
            log.error("Failed to register user: {}", username, e);
            return null;
        }
    }

    public User login(String username, String password) {
        User user = userCache.get(username);
        
        if (user == null) {
            Optional<User> found = userRepository.findByUsername(username);
            if (found.isPresent()) {
                user = found.get();
                userCache.put(username, user);
            }
        }
        
        if (user == null) {
            log.warn("Login failed: user not found: {}", username);
            return null;
        }
        
        if (!verifyPassword(password, user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user: {}", username);
            return null;
        }
        
        return user;
    }

    @Transactional
    public String createSession(String username) {
        String sid = generateToken();
        sessionCache.put(sid, username);
        
        try {
            Session session = new Session();
            session.setSid(sid);
            session.setUsername(username);
            session.setCreatedAt(Instant.now());
            sessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to create session in database: {}", username, e);
        }
        
        return sid;
    }

    @Transactional
    public void destroySession(String sid) {
        if (sid != null) {
            sessionCache.remove(sid);
            try {
                sessionRepository.deleteById(sid);
            } catch (Exception e) {
                log.error("Failed to delete session from database", e);
            }
        }
    }

    public User getUserBySession(String sid) {
        if (sid == null) return null;
        
        String username = sessionCache.get(sid);
        if (username == null) {
            try {
                Optional<Session> session = sessionRepository.findBySid(sid);
                if (session.isPresent()) {
                    username = session.get().getUsername();
                    sessionCache.put(sid, username);
                }
            } catch (Exception e) {
                log.error("Failed to get session from database", e);
            }
        }
        
        if (username == null) return null;
        
        User user = userCache.get(username);
        if (user == null) {
            Optional<User> found = userRepository.findByUsername(username);
            if (found.isPresent()) {
                user = found.get();
                userCache.put(username, user);
            }
        }
        
        return user;
    }

    public User getUserById(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        
        // Check cache first
        for (User u : userCache.values()) {
            if (u.getId().equals(userId)) {
                return u;
            }
        }
        
        // Check database
        try {
            Optional<User> found = userRepository.findById(userId);
            if (found.isPresent()) {
                User user = found.get();
                userCache.put(user.getUsername(), user);
                return user;
            } else {
                log.warn("User not found in DB: id={}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to load user by ID from database: {}", userId, e);
        }
        
        return null;
    }

    private static String hashPassword(String pwd) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(pwd.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyPassword(String pwd, String hash) {
        return hashPassword(pwd).equals(hash);
    }

    private static String generateToken() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
