package com.sondurukedella.security.auth;

import com.sondurukedella.security.user.UserAccountService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Problem details for the sign-in endpoints only; everything else goes through GlobalExceptionHandler.
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

    // always the same text, whatever actually failed
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AuthService.INVALID_LOGIN);
    }

    @ExceptionHandler(LoginThrottledException.class)
    ResponseEntity<ProblemDetail> handleThrottled(LoginThrottledException ex) {
        long seconds = Math.max(1, ex.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    // 400 rather than 401, so the app doesn't treat it as "your session ended"
    @ExceptionHandler(UserAccountService.WrongPasswordException.class)
    ProblemDetail handleWrongPassword(UserAccountService.WrongPasswordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
