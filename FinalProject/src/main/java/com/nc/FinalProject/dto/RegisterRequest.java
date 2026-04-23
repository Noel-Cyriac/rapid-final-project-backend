package com.nc.FinalProject.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "Date of birth is required")
    private LocalDate dob;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$",
            message = "Password must be 8+ chars, include uppercase, lowercase, number, special char and no spaces"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}