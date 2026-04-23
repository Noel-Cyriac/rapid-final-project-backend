package com.nc.FinalProject.exception;

import com.nc.FinalProject.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Validation errors (field-level)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );

        logger.error("Validation failed: {}", errors);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Validation failed", 400, errors));
    }

    // Email already exists
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        Map<String, String> errors = Collections.singletonMap("email", ex.getMessage());

        // Log to console
        logger.error("Email already exists: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Registration failed", 400, errors));
    }

    // Password mismatch
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordMismatch(PasswordMismatchException ex) {
        Map<String, String> errors = Collections.singletonMap("confirmPassword", ex.getMessage());

        // Log to console
        logger.error("Password mismatch: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Registration failed", 400, errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        Map<String, String> errors = Collections.singletonMap("general", "Invalid email or password");

        logger.warn("Login failed: Invalid credentials");

        return ResponseEntity.status(401)
                .body(new ErrorResponse("Login failed", 401, errors));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefresh(InvalidRefreshTokenException ex) {

        logger.warn("Invalid refresh token: {}", ex.getMessage());

        return ResponseEntity.status(401)
                .body(new ErrorResponse("Refresh failed", 401, null));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {

        logger.warn("Invalid or expired token: {}", ex.getMessage());

        return ResponseEntity.status(401)
                .body(new ErrorResponse("Link expired", 401, null));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponse> fileUploadException(FileUploadException ex) {
        logger.warn("File size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(413)
                .body(new ErrorResponse("File size exceeded", 413, null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Method not allowed: {}", ex.getMethod());
        return ResponseEntity.status(405)
                .body(new ErrorResponse(
                        "Method not allowed",
                        405,
                        Map.of("method", ex.getMethod() + " is not supported")
                ));
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(FileNotFoundException ex) {
        logger.warn("File not found: {}", ex.getMessage());
        return ResponseEntity.status(404)
                .body(new ErrorResponse("File error", 404, Map.of("file", ex.getMessage())));
    }


//    // Generic RuntimeException fallback (optional)
//    @ExceptionHandler(RuntimeException.class)
//    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
//        Map<String, String> errors = Collections.singletonMap("general", ex.getMessage());
//        logger.error("Runtime exception: ", ex);
//        return ResponseEntity.badRequest()
//                .body(new ErrorResponse("Error occurred", 400, errors));
//    }
}