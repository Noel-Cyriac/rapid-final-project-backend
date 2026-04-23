package com.nc.FinalProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String message;        // general message like "Registration failed"
    private int code;              // HTTP status code
    private Map<String, String> errors;  // field-specific errors
}
