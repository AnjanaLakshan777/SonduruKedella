package com.sondurukedella.security.auth;

import com.sondurukedella.security.user.UserAccountService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// error mapping just for AuthController
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

    // same message whatever actually went wrong
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

    // 400 not 401 - a 401 would make the frontend think the session expired
    @ExceptionHandler(UserAccountService.WrongPasswordException.class)
    ProblemDetail handleWrongPassword(UserAccountService.WrongPasswordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
