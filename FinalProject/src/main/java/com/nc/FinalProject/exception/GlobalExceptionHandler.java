package com.nc.FinalProject.exception;

import com.nc.FinalProject.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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

        // Constructor now only needs (message, errors)
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Validation failed", errors));
    }

    // Email already exists
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        Map<String, String> errors = Collections.singletonMap("email", ex.getMessage());
        logger.error("Email already exists: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT) // 409 is more accurate for existing data
                .body(new ErrorResponse("Registration failed", errors));
    }

    // Password mismatch
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordMismatch(PasswordMismatchException ex) {
        Map<String, String> errors = Collections.singletonMap("confirmPassword", ex.getMessage());
        logger.error("Password mismatch: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Password update failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        Map<String, String> errors = Collections.singletonMap("general", "Invalid email or password");
        logger.warn("Login failed: Invalid credentials");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Login failed", errors));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefresh(InvalidRefreshTokenException ex) {
        logger.warn("Invalid refresh token: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Refresh failed", null));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        logger.warn("Invalid or expired token: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Link expired", null));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponse> fileUploadException(FileUploadException ex) {
        logger.warn("File upload error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("File size exceeded", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Method not allowed: {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("Method not allowed", Map.of("method", ex.getMethod() + " is not supported")));
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(FileNotFoundException ex) {
        logger.warn("File not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("File error", Map.of("file", ex.getMessage())));
    }

    @ExceptionHandler(LinkDisabledException.class)
    public ResponseEntity<ErrorResponse> handleLinkDisabled(LinkDisabledException ex) {
        logger.warn("Link disabled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ErrorResponse> handleLinkExpired(LinkExpiredException ex) {
        logger.warn("Link expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    @ExceptionHandler(MaxUsesExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUses(MaxUsesExceededException ex) {
        logger.warn("Max uses exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidSharePasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidSharePasswordException ex) {
        logger.warn("Invalid share password attempt");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    @ExceptionHandler(SharedFileDeleteException.class)
    public ResponseEntity<ErrorResponse> handleSharedDelete(SharedFileDeleteException ex) {

        logger.warn("Shared file delete warning: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "Delete warning",
                        Map.of("sharedFiles", String.join(", ", ex.getSharedFiles()))
                ));
    }

    @ExceptionHandler(PasswordAlreadyViewedException.class)
    public ResponseEntity<ErrorResponse> handlePasswordViewed(
            PasswordAlreadyViewedException ex) {

        logger.warn("Password already viewed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    @ExceptionHandler(PasswordLinkExpiredException.class)
    public ResponseEntity<ErrorResponse> handlePasswordExpired(
            PasswordLinkExpiredException ex) {

        logger.warn("Password link expired: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse(ex.getMessage(), null));
    }

    // Generic Exception Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        logger.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred", null));
    }
}