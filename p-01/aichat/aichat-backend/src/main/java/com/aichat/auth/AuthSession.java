package com.aichat.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthSession {
    private AuthSession() {}

    public static final String USER_ID_KEY = "AICHAT_USER_ID";

    public static String requireUserId(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        Object userId = session.getAttribute(USER_ID_KEY);
        if (!(userId instanceof String s) || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return s;
    }

    public static void login(HttpSession session, String userId) {
        session.setAttribute(USER_ID_KEY, userId);
    }

    public static void logout(HttpSession session) {
        session.invalidate();
    }
}

