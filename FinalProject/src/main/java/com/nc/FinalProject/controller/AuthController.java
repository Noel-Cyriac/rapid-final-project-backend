package com.nc.FinalProject.controller;

import com.nc.FinalProject.dto.request.ForgotPasswordRequest;
import com.nc.FinalProject.dto.request.LoginRequest;
import com.nc.FinalProject.dto.request.RegisterRequest;
import com.nc.FinalProject.dto.request.ResetPasswordRequest;
import com.nc.FinalProject.dto.response.LoginResponse;
import com.nc.FinalProject.dto.response.SuccessResponse;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.security.JwtUtil;
import com.nc.FinalProject.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Users user = authService.register(request);
        return ResponseEntity.ok(
                new SuccessResponse("User registered successfully", Map.of("email", user.getEmail()))
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request, response);
        return ResponseEntity.ok(new SuccessResponse("Login successful", loginResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        LoginResponse res = authService.refreshToken(refreshToken, response);
        return ResponseEntity.ok(
                new SuccessResponse("Token refreshed successfully", res)
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        System.out.println(request);
        authService.sendResetPasswordEmail(request.getEmail());
        return ResponseEntity.ok(new SuccessResponse(
                "If an account with that email exists, a reset link has been sent.", null
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new SuccessResponse("Password reset successfully", null)
        );
    }
}