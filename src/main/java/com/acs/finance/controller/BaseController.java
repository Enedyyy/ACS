package com.acs.finance.controller;

import com.acs.finance.entity.User;
import com.acs.finance.exception.UnauthorizedException;
import com.acs.finance.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class BaseController {

    protected final AuthService authService;

    protected String getSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        
        for (Cookie cookie : cookies) {
            if ("SID".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    protected User getUser(HttpServletRequest request) {
        String sid = getSessionId(request);
        if (sid == null) return null;
        return authService.getUserBySession(sid);
    }

    protected User requireAuth(HttpServletRequest request) {
        User user = getUser(request);
        if (user == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    protected static String escapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
