package com.nc.FinalProject.service;

import com.nc.FinalProject.dto.request.ChangePasswordRequest;
import com.nc.FinalProject.dto.request.LoginRequest;
import com.nc.FinalProject.dto.request.RegisterRequest;
import com.nc.FinalProject.dto.request.ResetPasswordRequest;
import com.nc.FinalProject.dto.response.LoginResponse;
import com.nc.FinalProject.dto.response.RefreshResponse;
import com.nc.FinalProject.entity.*;
import com.nc.FinalProject.exception.*;
import com.nc.FinalProject.repository.PasswordResetTokenRepository;
import com.nc.FinalProject.repository.RefreshTokenRepository;
import com.nc.FinalProject.repository.UserRepository;
import com.nc.FinalProject.security.CustomUserDetails;
import com.nc.FinalProject.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private MailService mailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", 86400000L); // 24 hours
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password");
        request.setConfirmPassword("password");
        request.setDob(LocalDate.of(1990, 1, 1));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Users result = authService.register(request);

        assertNotNull(result);
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("john@example.com", result.getEmail());
        assertEquals("encodedPassword", result.getPassword());
        verify(userRepository).save(any(Users.class));
    }

    @Test
    void register_ThrowsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password");
        request.setConfirmPassword("password");
        request.setDob(LocalDate.of(1990, 1, 1));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new Users()));

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void register_ThrowsPasswordMismatchException() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPassword("password");
        request.setConfirmPassword("different");
        request.setDob(LocalDate.of(1990, 1, 1));

        assertThrows(PasswordMismatchException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("john@example.com", "password");
        HttpServletResponse response = mock(HttpServletResponse.class);

        Users user = Users.builder()
                .id(1L)
                .email("john@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user.getEmail(), user.getPassword());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken("john@example.com")).thenReturn("accessToken");
        when(jwtUtil.generateRefreshToken("john@example.com")).thenReturn("refreshToken");

        LoginResponse result = authService.login(request, response);

        assertNotNull(result);
        assertEquals("accessToken", result.getAccessToken());
        assertEquals("John", result.getFirstName());
        assertEquals("john@example.com", result.getEmail());

        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(response).addCookie(any(Cookie.class));
    }

    @Test
    void login_ThrowsBadCredentialsExceptionWhenUserNotFound() {
        LoginRequest request = new LoginRequest("john@example.com", "password");
        HttpServletResponse response = mock(HttpServletResponse.class);

        Users user = Users.builder().email("john@example.com").password("encodedPassword").build();
        CustomUserDetails userDetails = new CustomUserDetails(user.getEmail(), user.getPassword());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request, response));
    }

    @Test
    void refreshToken_Success() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        Users user = Users.builder().email("john@example.com").build();
        RefreshToken storedToken = RefreshToken.builder()
                .token("oldRefreshToken")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByToken("oldRefreshToken")).thenReturn(Optional.of(storedToken));
        when(jwtUtil.validateRefreshToken("oldRefreshToken")).thenReturn(true);
        when(jwtUtil.generateAccessToken("john@example.com")).thenReturn("newAccessToken");
        when(jwtUtil.generateRefreshToken("john@example.com")).thenReturn("newRefreshToken");

        RefreshResponse result = authService.refreshToken("oldRefreshToken", response);

        assertNotNull(result);
        assertEquals("newAccessToken", result.getAccessToken());
        verify(refreshTokenRepository).save(storedToken);
        assertEquals("newRefreshToken", storedToken.getToken());
        verify(response).addCookie(any(Cookie.class));
    }

    @Test
    void refreshToken_ThrowsInvalidRefreshTokenExceptionWhenMissing() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertThrows(InvalidRefreshTokenException.class, () -> authService.refreshToken(null, response));
    }

    @Test
    void refreshToken_ThrowsInvalidRefreshTokenExceptionWhenTokenRevoked() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refreshTokenRepository.findByToken("revoked")).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refreshToken("revoked", response));
    }

    @Test
    void refreshToken_ThrowsInvalidRefreshTokenExceptionWhenExpired() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired")
                .expiryDate(Instant.now().minusSeconds(10))
                .build();
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expiredToken));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refreshToken("expired", response));
        verify(refreshTokenRepository).delete(expiredToken);
    }

    @Test
    void sendResetPasswordEmail_Success() {
        Users user = Users.builder().id(1L).email("john@example.com").build();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        authService.sendResetPasswordEmail("john@example.com");

        verify(resetTokenRepository).deleteByUser(user);
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendResetPasswordEmail(eq("john@example.com"), anyString());
    }

    @Test
    void resetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token123");
        request.setNewPassword("newPass");
        request.setConfirmPassword("newPass");

        Users user = Users.builder().id(1L).email("john@example.com").build();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token("token123")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();

        when(resetTokenRepository.findByToken("token123")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        authService.resetPassword(request);

        assertEquals("encodedNewPass", user.getPassword());
        verify(userRepository).save(user);
        verify(resetTokenRepository).delete(resetToken);
        verify(notificationService).create(eq(user), anyString(), anyString(), eq(NotificationType.SECURITY));
    }

    @Test
    void resetPassword_ThrowsPasswordMismatchException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token123");
        request.setNewPassword("newPass");
        request.setConfirmPassword("diffPass");

        assertThrows(PasswordMismatchException.class, () -> authService.resetPassword(request));
    }

    @Test
    void changePassword_Success() {
        Users user = Users.builder().id(1L).email("john@example.com").password("oldEncoded").build();
        when(passwordEncoder.matches("oldPass", "oldEncoded")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("newEncoded");

        authService.changePassword(user, "oldPass", "newPass", "newPass");

        assertEquals("newEncoded", user.getPassword());
        verify(userRepository).save(user);
        verify(notificationService).create(eq(user), anyString(), anyString(), eq(NotificationType.SECURITY));
    }

    @Test
    void changePassword_ThrowsPasswordMismatchExceptionWhenOldIncorrect() {
        Users user = Users.builder().id(1L).email("john@example.com").password("oldEncoded").build();
        when(passwordEncoder.matches("wrongPass", "oldEncoded")).thenReturn(false);

        assertThrows(PasswordMismatchException.class, () -> authService.changePassword(user, "wrongPass", "newPass", "newPass"));
    }

    @Test
    void logout_Success() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        authService.logout("someToken", response);

        verify(refreshTokenRepository).deleteByToken("someToken");
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());

        Cookie capturedCookie = cookieCaptor.getValue();
        assertEquals("refreshToken", capturedCookie.getName());
        assertNull(capturedCookie.getValue());
        assertEquals(0, capturedCookie.getMaxAge());
    }
}
