package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user");

        // Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        // Check if user is active
        if (!user.getActive()) {
            log.warn("Login attempt for inactive user");
            throw new UnauthorizedException("User account is inactive");
        }

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password attempt");
            throw new UnauthorizedException("Invalid username or password");
        }

        // Convert and normalize client type
        ClientType clientType = parseClientType(request.getClientType());

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), clientType);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), clientType);

        // Get token expiration times
        long accessTokenExpiration = jwtTokenProvider.getTokenExpiration(clientType, TokenType.ACCESS);
        long refreshTokenExpiration = jwtTokenProvider.getTokenExpiration(clientType, TokenType.REFRESH);

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Build user response
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setUsername(user.getUsername());
        userResponse.setEmail(user.getEmail());
        userResponse.setActive(user.getActive());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setLastLoginAt(user.getLastLoginAt());

        // Build auth response
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType(AuthResponse.DEFAULT_TOKEN_TYPE);
        response.setExpiresIn(accessTokenExpiration);
        response.setRefreshExpiresIn(refreshTokenExpiration);
        response.setClientType(clientType.name());
        response.setUser(userResponse);

        log.info("User logged in successfully: userId={}", user.getId());
        return response;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("Registration failed: username already exists: {}", request.getUsername());
            throw new ConflictException("Registration failed: username already exists: " + request.getUsername());
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed: email already exists: {}", request.getEmail());
            throw new ConflictException("Registration failed: username or email already exists: " + request.getEmail());
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        // Save user
        user = userRepository.save(user);

        // Build user response
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setActive(user.getActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());

        log.info("User registered successfully: userId={}, username={}", user.getId(), user.getUsername());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        log.info("Token refresh attempt");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            log.warn("Invalid refresh token");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Extract user information from token
        UUID userId;
        String username;
        ClientType clientType;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
            username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());
            clientType = jwtTokenProvider.getClientTypeFromToken(request.getRefreshToken());
        } catch (InvalidTokenException e) {
            log.warn("Invalid token during refresh: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired refresh token", e);
        }

        // Ensure userId is not null (should never happen with valid token, but
        // satisfies type safety)
        Objects.requireNonNull(userId, "User ID cannot be null");

        // Verify user exists and is active
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getActive()) {
            log.warn("Token refresh attempt for inactive user: userId={}", userId);
            throw new UnauthorizedException("User account is inactive");
        }

        // Generate new access token
        String accessToken = jwtTokenProvider.generateAccessToken(userId, username, clientType);
        long accessTokenExpiration = jwtTokenProvider.getTokenExpiration(clientType, TokenType.ACCESS);

        // Build response
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setAccessToken(accessToken);
        response.setTokenType(RefreshTokenResponse.DEFAULT_TOKEN_TYPE);
        response.setExpiresIn(accessTokenExpiration);

        log.info("Token refreshed successfully: userId={}", userId);
        return response;
    }

    /**
     * Parse a string client type to ClientType enum.
     * Defaults to WEB if the string is null, empty, or invalid.
     *
     * @param clientTypeStr String representation of client type
     * @return ClientType enum value
     */
    private ClientType parseClientType(String clientTypeStr) {
        if (clientTypeStr == null || clientTypeStr.isEmpty()) {
            return ClientType.WEB;
        }
        try {
            return ClientType.valueOf(clientTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid client type: {}, defaulting to WEB", clientTypeStr);
            return ClientType.WEB;
        }
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.info("Logout attempt");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }

        // Validate token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("Invalid or expired refresh token during logout");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        // Extract user ID from token (prevents unauthorized invalidation of other
        // users' tokens)
        UUID userId;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        } catch (InvalidTokenException e) {
            log.warn("Failed to extract user ID from refresh token: {}", e.getMessage());
            throw new UnauthorizedException("Invalid refresh token", e);
        }

        // Ensure userId is not null (should never happen with valid token, but
        // satisfies type safety)
        Objects.requireNonNull(userId, "User ID cannot be null");

        // Verify user exists
        if (!userRepository.existsById(userId)) {
            log.warn("User not found during logout: userId={}", userId);
            throw new ResourceNotFoundException("User not found: " + userId, userId);
        }

        // Note: Token blacklist would be implemented here if needed
        // For now, we just validate the token and return
        // The token will naturally expire based on its expiration time

        log.info("User logged out successfully: userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        log.info("Get current user: userId={}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId, userId));

        // Build user response
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setActive(user.getActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());

        return response;
    }
}
