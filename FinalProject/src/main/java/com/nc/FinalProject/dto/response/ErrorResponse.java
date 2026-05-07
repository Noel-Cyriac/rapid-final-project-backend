package com.nc.FinalProject.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
public class ErrorResponse {
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now(); // Auto-generates on instantiation
    private Map<String, String> errors;

    // Custom constructor since we don't want to pass timestamp manually
    public ErrorResponse(String message, Map<String, String> errors) {
        this.message = message;
        this.errors = errors;
        // timestamp is already initialized above
    }
}