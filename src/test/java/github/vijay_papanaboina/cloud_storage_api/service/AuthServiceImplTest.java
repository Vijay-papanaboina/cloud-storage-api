package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.LoginRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.RefreshTokenRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.RegisterRequest;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl focusing on error contracts and edge cases.
 * Success scenarios are covered by integration tests.
 * These tests verify what exceptions are thrown, not how the code works
 * internally.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private UUID userId;
    private String username;
    private String email;
    private String password;
    private String passwordHash;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "testuser";
        email = "test@example.com";
        password = "password123";
        passwordHash = "$2a$10$hashedPasswordHash";

        testUser = createTestUser(userId, username, email, passwordHash);
    }

    // ==================== Login Error Contract Tests ====================

    @Test
    void login_UserNotFound_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_InactiveUser_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password);
        testUser.setActive(false);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User account is inactive");
    }

    @Test
    void login_InvalidPassword_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    // ==================== Register Error Contract Tests ====================

    @Test
    void register_DuplicateUsername_ThrowsConflictException() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(github.vijay_papanaboina.cloud_storage_api.exception.ConflictException.class)
                .hasMessageContaining("username already exists");
    }

    @Test
    void register_DuplicateEmail_ThrowsConflictException() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(github.vijay_papanaboina.cloud_storage_api.exception.ConflictException.class)
                .hasMessageContaining("email already exists");
    }

    // ==================== RefreshToken Error Contract Tests ====================

    @Test
    void refreshToken_InvalidToken_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "invalid-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshToken_UserNotFound_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void refreshToken_InactiveUser_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        testUser.setActive(false);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User account is inactive");
    }

    @Test
    void refreshToken_ExtractUserIdFails_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken))
                .thenThrow(new github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException(
                        "Token expired"));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshToken_NullUserId_ThrowsNullPointerException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // ==================== Logout Error Contract Tests ====================

    @Test
    void logout_NullToken_ThrowsBadRequestException() {
        // When/Then
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refresh token is required");
    }

    @Test
    void logout_BlankToken_ThrowsBadRequestException() {
        // When/Then
        assertThatThrownBy(() -> authService.logout("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refresh token is required");
    }

    @Test
    void logout_InvalidToken_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "invalid-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void logout_ExtractUserIdFails_ThrowsUnauthorizedException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken))
                .thenThrow(new github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException(
                        "Token expired"));

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void logout_NullUserId_ThrowsIllegalArgumentException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void logout_UserNotFound_ThrowsResourceNotFoundException()
            throws github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(userRepository.existsById(userId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== GetCurrentUser Error Contract Tests ====================

    @Test
    void getCurrentUser_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void getCurrentUser_UserNotFound_ThrowsResourceNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== Helper Methods ====================

    private User createTestUser(UUID userId, String username, String email, String passwordHash) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(null);
        return user;
    }

    private LoginRequest createTestLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private RegisterRequest createTestRegisterRequest(String username, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
