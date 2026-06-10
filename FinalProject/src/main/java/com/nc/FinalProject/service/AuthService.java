package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.LoginRequest;
import com.nc.FinalProject.dto.response.LoginResponse;
import com.nc.FinalProject.dto.request.RegisterRequest;
import com.nc.FinalProject.dto.request.ResetPasswordRequest;
import com.nc.FinalProject.dto.response.RefreshResponse;
import com.nc.FinalProject.entity.NotificationType;
import com.nc.FinalProject.entity.PasswordResetToken;
import com.nc.FinalProject.entity.RefreshToken;
import com.nc.FinalProject.entity.Users;
import com.nc.FinalProject.exception.EmailAlreadyExistsException;
import com.nc.FinalProject.exception.InvalidRefreshTokenException;
import com.nc.FinalProject.exception.InvalidTokenException;
import com.nc.FinalProject.exception.PasswordMismatchException;
import com.nc.FinalProject.repository.PasswordResetTokenRepository;
import com.nc.FinalProject.repository.RefreshTokenRepository;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.security.CustomUserDetails;
import com.nc.FinalProject.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final NotificationService notificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;
    private final PasswordResetTokenRepository resetTokenRepository;


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

    @Transactional
    public LoginResponse login(
            LoginRequest request,
            HttpServletResponse response
    ) {

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
                .orElseThrow(() ->
                        new BadCredentialsException("User not found"));

        String accessToken =
                jwtUtil.generateAccessToken(email);

        String refreshToken =
                jwtUtil.generateRefreshToken(email);

        // one login = one refresh token
        refreshTokenRepository.deleteByUser(user);

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiryDate(
                                Instant.now()
                                        .plusMillis(refreshExpiration)
                        )
                        .build()
        );

        Cookie refreshCookie =
                new Cookie("refreshToken", refreshToken);

        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false); // true in prod
        refreshCookie.setPath("/api/auth");
        refreshCookie.setMaxAge(
                (int) (refreshExpiration / 1000)
        );

        response.addCookie(refreshCookie);

        logger.info(
                "Login SUCCESS for user: {}",
                email
        );

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
            throw new InvalidRefreshTokenException("Missing refresh token");
        }

        // 1. DB FIRST (source of truth)
        RefreshToken storedToken =
                refreshTokenRepository.findByToken(refreshToken)
                        .orElseThrow(() -> new InvalidRefreshTokenException("Token revoked"));

        // 2. DB expiry check
        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidRefreshTokenException("Token expired");
        }

        // 3. JWT validation (optional safety layer)
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidRefreshTokenException("Invalid token");
        }

        String email = storedToken.getUser().getEmail();

        String newAccessToken = jwtUtil.generateAccessToken(email);
        String newRefreshToken = jwtUtil.generateRefreshToken(email);

        // 4. ROTATE TOKEN (update same DB row)
        storedToken.setToken(newRefreshToken);
        storedToken.setExpiryDate(
                Instant.now().plusMillis(refreshExpiration)
        );

        refreshTokenRepository.save(storedToken);

        Cookie cookie = new Cookie("refreshToken", newRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (refreshExpiration / 1000));

        response.addCookie(cookie);

        return new RefreshResponse(newAccessToken);
    }

    @Transactional
    public void sendResetPasswordEmail(String email) {

        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) return;

        // optional: remove old tokens
        resetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(15 * 60)) // 15 min expiry
                .build();

        resetTokenRepository.save(resetToken);

        String resetLink = "http://localhost:5175/reset-password/" + token;

        mailService.sendResetPasswordEmail(user.getEmail(), resetLink);
    }

    public void resetPassword(ResetPasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        PasswordResetToken resetToken =
                resetTokenRepository.findByToken(request.getToken())
                        .orElseThrow(() ->
                                new InvalidTokenException("Invalid reset token")
                        );

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            resetTokenRepository.delete(resetToken);
            throw new InvalidTokenException("Reset token expired");
        }

        Users user = resetToken.getUser();

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // invalidate token after use
        resetTokenRepository.delete(resetToken);

        notificationService.create(
                user,
                "Password Reset Successful",
                "Your password was reset using email link",
                NotificationType.SECURITY
        );
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

    @Transactional
    public void logout(String refreshToken, HttpServletResponse response) {

        if (refreshToken != null) {
            refreshTokenRepository.deleteByToken(refreshToken);
        }

        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }
}