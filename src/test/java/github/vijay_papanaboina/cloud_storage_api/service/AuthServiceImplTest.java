package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.AuthResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.LoginRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.RefreshTokenRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.RefreshTokenResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.RegisterRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.UserResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ConflictException;
import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;
import github.vijay_papanaboina.cloud_storage_api.model.ClientType;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

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

    // login tests
    @Test
    void login_Success_WebClient() throws InvalidTokenException {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "WEB");
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        long accessTokenExpiration = 900000L; // 15 minutes
        long refreshTokenExpiration = 604800000L; // 7 days

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.WEB)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId, username, ClientType.WEB)).thenReturn(refreshToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.ACCESS)).thenReturn(accessTokenExpiration);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.REFRESH)).thenReturn(refreshTokenExpiration);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(accessToken);
        assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(response.getTokenType()).isEqualTo(AuthResponse.DEFAULT_TOKEN_TYPE);
        assertThat(response.getExpiresIn()).isEqualTo(accessTokenExpiration);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(refreshTokenExpiration);
        assertThat(response.getClientType()).isEqualTo("WEB");
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo(userId);
        assertThat(response.getUser().getUsername()).isEqualTo(username);

        verify(userRepository, times(1)).findByUsername(username);
        verify(passwordEncoder, times(1)).matches(password, passwordHash);
        verify(jwtTokenProvider, times(1)).generateAccessToken(userId, username, ClientType.WEB);
        verify(jwtTokenProvider, times(1)).generateRefreshToken(userId, username, ClientType.WEB);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void login_Success_CliClient() throws InvalidTokenException {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "CLI");
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        long accessTokenExpiration = 86400000L; // 1 day
        long refreshTokenExpiration = 7776000000L; // 90 days

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.CLI)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId, username, ClientType.CLI)).thenReturn(refreshToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.CLI, TokenType.ACCESS)).thenReturn(accessTokenExpiration);
        when(jwtTokenProvider.getTokenExpiration(ClientType.CLI, TokenType.REFRESH)).thenReturn(refreshTokenExpiration);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getClientType()).isEqualTo("CLI");
        assertThat(response.getExpiresIn()).isEqualTo(accessTokenExpiration);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(refreshTokenExpiration);

        verify(jwtTokenProvider, times(1)).generateAccessToken(userId, username, ClientType.CLI);
        verify(jwtTokenProvider, times(1)).generateRefreshToken(userId, username, ClientType.CLI);
    }

    @Test
    void login_Success_NullClientType_DefaultsToWeb() throws InvalidTokenException {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, null);
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        long accessTokenExpiration = 900000L;
        long refreshTokenExpiration = 604800000L;

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.WEB)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId, username, ClientType.WEB)).thenReturn(refreshToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.ACCESS)).thenReturn(accessTokenExpiration);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.REFRESH)).thenReturn(refreshTokenExpiration);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getClientType()).isEqualTo("WEB");

        verify(jwtTokenProvider, times(1)).generateAccessToken(userId, username, ClientType.WEB);
    }

    @Test
    void login_Success_InvalidClientType_DefaultsToWeb() throws InvalidTokenException {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "INVALID");
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        long accessTokenExpiration = 900000L;
        long refreshTokenExpiration = 604800000L;

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.WEB)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId, username, ClientType.WEB)).thenReturn(refreshToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.ACCESS)).thenReturn(accessTokenExpiration);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.REFRESH)).thenReturn(refreshTokenExpiration);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getClientType()).isEqualTo("WEB");

        verify(jwtTokenProvider, times(1)).generateAccessToken(userId, username, ClientType.WEB);
    }

    @Test
    void login_UserNotFound_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "WEB");
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userRepository, times(1)).findByUsername(username);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_InactiveUser_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "WEB");
        testUser.setActive(false);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User account is inactive");

        verify(userRepository, times(1)).findByUsername(username);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_InvalidPassword_ThrowsUnauthorizedException() {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "WEB");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userRepository, times(1)).findByUsername(username);
        verify(passwordEncoder, times(1)).matches(password, passwordHash);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_UpdatesLastLoginAt() throws InvalidTokenException {
        // Given
        LoginRequest request = createTestLoginRequest(username, password, "WEB");
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        long accessTokenExpiration = 900000L;
        long refreshTokenExpiration = 604800000L;

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, passwordHash)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.WEB)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId, username, ClientType.WEB)).thenReturn(refreshToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.ACCESS)).thenReturn(accessTokenExpiration);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.REFRESH)).thenReturn(refreshTokenExpiration);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        authService.login(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLoginAt()).isNotNull();
    }

    // register tests
    @Test
    void register_Success() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        String encodedPassword = "$2a$10$encodedPasswordHash";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        // When
        UserResponse response = authService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();

        verify(userRepository, times(1)).findByUsername(username);
        verify(userRepository, times(1)).findByEmail(email);
        verify(passwordEncoder, times(1)).encode(password);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateUsername_ThrowsConflictException() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("username already exists");

        verify(userRepository, times(1)).findByUsername(username);
        verify(userRepository, never()).findByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicateEmail_ThrowsConflictException() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email already exists");

        verify(userRepository, times(1)).findByUsername(username);
        verify(userRepository, times(1)).findByEmail(email);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_PasswordIsHashed() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        String encodedPassword = "$2a$10$encodedPasswordHash";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(encodedPassword);
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(password);
    }

    @Test
    void register_UserActivatedByDefault() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        String encodedPassword = "$2a$10$encodedPasswordHash";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        // When
        UserResponse response = authService.register(request);

        // Then
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void register_CreatedAtSet() {
        // Given
        RegisterRequest request = createTestRegisterRequest(username, email, password);
        String encodedPassword = "$2a$10$encodedPasswordHash";
        Instant beforeRegister = Instant.now();

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        // When
        UserResponse response = authService.register(request);

        // Then
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getCreatedAt()).isAfterOrEqualTo(beforeRegister);
    }

    // refreshToken tests
    @Test
    void refreshToken_Success() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        String newAccessToken = "new-access-token";
        long accessTokenExpiration = 900000L;

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(jwtTokenProvider.getClientTypeFromToken(refreshToken)).thenReturn(ClientType.WEB);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(userId, username, ClientType.WEB)).thenReturn(newAccessToken);
        when(jwtTokenProvider.getTokenExpiration(ClientType.WEB, TokenType.ACCESS)).thenReturn(accessTokenExpiration);

        // When
        RefreshTokenResponse response = authService.refreshToken(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(response.getTokenType()).isEqualTo(RefreshTokenResponse.DEFAULT_TOKEN_TYPE);
        assertThat(response.getExpiresIn()).isEqualTo(accessTokenExpiration);

        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
        verify(jwtTokenProvider, times(1)).generateAccessToken(userId, username, ClientType.WEB);
    }

    @Test
    void refreshToken_InvalidToken_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "invalid-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");

        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, never()).getUserIdFromToken(anyString());
    }

    @Test
    void refreshToken_UserNotFound_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(jwtTokenProvider.getClientTypeFromToken(refreshToken)).thenReturn(ClientType.WEB);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refreshToken_InactiveUser_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        testUser.setActive(false);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(jwtTokenProvider.getClientTypeFromToken(refreshToken)).thenReturn(ClientType.WEB);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User account is inactive");

        verify(userRepository, times(1)).findById(userId);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refreshToken_ExtractUserIdFails_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken))
                .thenThrow(new InvalidTokenException("Token expired"));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");

        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refreshToken_NullUserId_ThrowsNullPointerException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
    }

    // logout tests
    @Test
    void logout_Success() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(userRepository.existsById(userId)).thenReturn(true);

        // When
        authService.logout(refreshToken);

        // Then
        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
        verify(userRepository, times(1)).existsById(userId);
    }

    @Test
    void logout_NullToken_ThrowsBadRequestException() {
        // When/Then
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refresh token is required");

        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    void logout_BlankToken_ThrowsBadRequestException() {
        // When/Then
        assertThatThrownBy(() -> authService.logout("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refresh token is required");

        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    void logout_InvalidToken_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "invalid-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");

        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, never()).getUserIdFromToken(anyString());
    }

    @Test
    void logout_ExtractUserIdFails_ThrowsUnauthorizedException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken))
                .thenThrow(new InvalidTokenException("Token expired"));

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");

        verify(jwtTokenProvider, times(1)).validateToken(refreshToken);
        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
        verify(userRepository, never()).existsById(any());
    }

    @Test
    void logout_NullUserId_ThrowsIllegalArgumentException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(jwtTokenProvider, times(1)).getUserIdFromToken(refreshToken);
    }

    @Test
    void logout_UserNotFound_ThrowsResourceNotFoundException() throws InvalidTokenException {
        // Given
        String refreshToken = "refresh-token";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(userRepository.existsById(userId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).existsById(userId);
    }

    // getCurrentUser tests
    @Test
    void getCurrentUser_Success() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = authService.getCurrentUser(userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getActive()).isEqualTo(testUser.getActive());
        assertThat(response.getCreatedAt()).isEqualTo(testUser.getCreatedAt());
        assertThat(response.getLastLoginAt()).isEqualTo(testUser.getLastLoginAt());

        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void getCurrentUser_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void getCurrentUser_UserNotFound_ThrowsResourceNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
    }

    // Helper methods
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

    private LoginRequest createTestLoginRequest(String username, String password, String clientType) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setClientType(clientType);
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
