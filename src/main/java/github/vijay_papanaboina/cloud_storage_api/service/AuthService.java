package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ConflictException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;

import java.util.UUID;

public interface AuthService {
    /**
     * Authenticate user and return JWT tokens
     *
     * @param request Login request with username, password, and optional clientType
     * @return AuthResponse with access token, refresh token, and user information
     * @throws UnauthorizedException if credentials are invalid or user is inactive
     * @throws BadRequestException   if request validation fails
     */
    AuthResponse login(LoginRequest request);

    /**
     * Register a new user
     *
     * @param request Registration request with username, email, and password
     * @return UserResponse with created user information
     * @throws ConflictException   if username or email already exists
     * @throws BadRequestException if request validation fails
     */
    UserResponse register(RegisterRequest request);

    /**
     * Refresh access token using refresh token
     *
     * @param request Refresh token request
     * @return RefreshTokenResponse with new access token
     * @throws UnauthorizedException if refresh token is invalid or expired
     * @throws BadRequestException   if request validation fails
     */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    /**
     * Logout user and invalidate refresh token.
     * The user ID is extracted from the refresh token itself to prevent
     * unauthorized token invalidation for other users.
     *
     * @param refreshToken Refresh token to invalidate
     * @throws BadRequestException       if refresh token is missing or empty
     * @throws UnauthorizedException     if refresh token is invalid or expired
     * @throws ResourceNotFoundException if the user extracted from the token does
     *                                   not exist
     */
    void logout(String refreshToken);

    /**
     * Get current authenticated user information
     *
     * @param userId User ID
     * @return UserResponse with user information
     * @throws ResourceNotFoundException if user is not found
     */
    UserResponse getCurrentUser(UUID userId);
}
