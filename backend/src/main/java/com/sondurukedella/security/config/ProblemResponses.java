package com.sondurukedella.security.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

// JSON 401/403 for the security filters, same shape as GlobalExceptionHandler.
// No WWW-Authenticate header, otherwise the browser shows its basic-auth popup.
final class ProblemResponses {

    private ProblemResponses() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Sign in to continue.");
    }

    // CSRF failures end up here too
    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "You don't have permission to do this.");
    }

    private static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
