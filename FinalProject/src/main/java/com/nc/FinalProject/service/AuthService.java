package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.LoginRequest;
import com.nc.FinalProject.dto.response.LoginResponse;
import com.nc.FinalProject.dto.request.RegisterRequest;
import com.nc.FinalProject.dto.request.ResetPasswordRequest;
import com.nc.FinalProject.dto.response.RefreshResponse;
import com.nc.FinalProject.entity.NotificationType;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.exception.EmailAlreadyExistsException;
import com.nc.FinalProject.exception.InvalidRefreshTokenException;
import com.nc.FinalProject.exception.InvalidTokenException;
import com.nc.FinalProject.exception.PasswordMismatchException;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.security.CustomUserDetails;
import com.nc.FinalProject.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    // Map to store reset tokens temporarily (replace with DB in production)
    private final Map<String, String> resetTokens = new HashMap<>();
    private final NotificationService notificationService;


    public Users register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        Users user = Users.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .dob(request.getDob())
                .build();

        Users savedUser = userRepository.save(user);

        // Log successful registration
        logger.info("New user registered: {}", savedUser.getEmail());

        return savedUser;
    }


    public LoginResponse login(LoginRequest request, HttpServletResponse response) {

        var auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().trim(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails =
                (CustomUserDetails) auth.getPrincipal();

        String email = userDetails.getUsername();

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtUtil.generateAccessToken(email);
        String refreshToken = jwtUtil.generateRefreshToken(email);

        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60);

        response.addCookie(refreshCookie);

        logger.info("Login SUCCESS for user: {}", email);

        return new LoginResponse(
                accessToken,
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }
    public RefreshResponse refreshToken(
            String refreshToken,
            HttpServletResponse response
    ) {

        if (refreshToken == null) {
            logger.warn("Refresh FAILED: Missing token");
            throw new InvalidRefreshTokenException(
                    "Refresh token missing"
            );
        }

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            logger.warn("Refresh FAILED: Invalid or expired token");

            throw new InvalidRefreshTokenException(
                    "Invalid or expired refresh token"
            );
        }

        String email =
                jwtUtil.extractEmail(refreshToken);

        String newAccessToken =
                jwtUtil.generateAccessToken(email);

        String newRefreshToken =
                jwtUtil.generateRefreshToken(email);

        Cookie refreshCookie =
                new Cookie(
                        "refreshToken",
                        newRefreshToken
                );

        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(
                (int) (jwtUtil.getRefreshExpiration() / 1000)
        );

        response.addCookie(refreshCookie);

        logger.info("Refresh SUCCESS for user: {}", email);

        return new RefreshResponse(newAccessToken);
    }
    public void sendResetPasswordEmail(String email) {

        logger.info("Received request to send reset password email for: {}", email);

        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            logger.warn("No user found with email: {}. Skipping email send.", email);
            return;
        }

        String token = UUID.randomUUID().toString();
        resetTokens.put(token, user.getEmail());

        String resetLink = "http://localhost:5175/reset-password/" + token;

        logger.info("Generated reset link for {}: {}", email, resetLink);

        // ✅ ACTUAL EMAIL SEND USING YOUR MAIL SERVICE
        mailService.sendResetPasswordEmail(user.getEmail(), resetLink);

        logger.info("Reset password email sent successfully to {}", email);
    }

    public void resetPassword(ResetPasswordRequest request) {
        logger.info("Received reset token: {}", request.getToken());

        // ✅ Check password match here
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        // ✅ Check token
        String email = resetTokens.get(request.getToken());
        if (email == null) {
            throw new InvalidTokenException("Invalid or expired reset token");
        }


// ✅ Create a Users object with only email set (or fetch it if needed)
        Users user = userRepository.findByEmail(email).orElse(null);

// ✅ Update password
        if (user != null) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);


        // ✅ Invalidate token
        resetTokens.remove(request.getToken());
        logger.info("Password reset successful for user: {}", email);

            notificationService.create(
                    user,
                    "Password Reset Successful",
                    "Your password was reset using email link",
                    NotificationType.SECURITY
            );
    }
    }

    public void changePassword(Users user, String oldPassword, String newPassword, String confirmPassword) {

        logger.info("Changing password for user: {}", user.getEmail());

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new PasswordMismatchException("Old password is incorrect");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException("New passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Password successfully updated for user: {}", user.getEmail());

        notificationService.create(
                user,
                "Password Changed",
                "Your password was updated successfully",
                NotificationType.SECURITY
        );
    }
}