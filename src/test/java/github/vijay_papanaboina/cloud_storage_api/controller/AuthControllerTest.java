package github.vijay_papanaboina.cloud_storage_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.ConflictException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.ApiKeyService;
import github.vijay_papanaboina.cloud_storage_api.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ApiKeyService apiKeyService;

    private UUID userId;
    private String username;
    private String email;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "testuser";
        email = "test@example.com";
    }

    // POST /api/auth/login tests
    @Test
    void login_Success_WebClient_Returns200() throws Exception {
        // Given
        LoginRequest request = new LoginRequest(username, "password123", "WEB");
        AuthResponse authResponse = createTestAuthResponse("access-token", "refresh-token", "WEB");

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.clientType").value("WEB"))
                .andExpect(jsonPath("$.user.username").value(username));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    void login_Success_CliClient_Returns200() throws Exception {
        // Given
        LoginRequest request = new LoginRequest(username, "password123", "CLI");
        AuthResponse authResponse = createTestAuthResponse("access-token", "refresh-token", "CLI");

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientType").value("CLI"));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    void login_MissingUsername_Returns400() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setPassword("password123");

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_MissingPassword_Returns400() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setUsername(username);

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_InvalidCredentials_Returns401() throws Exception {
        // Given
        LoginRequest request = new LoginRequest(username, "wrongpassword", "WEB");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid username or password"));

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    // POST /api/auth/register tests
    @Test
    void register_Success_Returns201() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(username, email, "password123");
        UserResponse userResponse = createTestUserResponse();

        when(authService.register(any(RegisterRequest.class))).thenReturn(userResponse);

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    void register_MissingUsername_Returns400() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_MissingEmail_Returns400() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("password123");

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_MissingPassword_Returns400() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_InvalidEmail_Returns400() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail("invalid-email");
        request.setPassword("password123");

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_DuplicateUsername_Returns409() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(username, email, "password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Registration failed: username already exists: " + username));

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    // POST /api/auth/refresh tests
    @Test
    void refreshToken_Success_Returns200() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setAccessToken("new-access-token");
        response.setTokenType("Bearer");
        response.setExpiresIn(900000L);

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService, times(1)).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshToken_MissingToken_Returns400() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("");

        // When/Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refreshToken(any());
    }

    @Test
    void refreshToken_InvalidToken_Returns401() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid or expired refresh token"));

        // When/Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(authService, times(1)).refreshToken(any(RefreshTokenRequest.class));
    }

    // POST /api/auth/logout tests
    @Test
    void logout_Success_Returns204() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        doNothing().when(authService).logout(anyString());

        // When/Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService, times(1)).logout(eq("refresh-token"));
    }

    @Test
    void logout_MissingToken_Returns400() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("");

        // When/Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).logout(anyString());
    }

    @Test
    void logout_InvalidToken_Returns401() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        doThrow(new UnauthorizedException("Invalid or expired refresh token"))
                .when(authService).logout(anyString());

        // When/Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(authService, times(1)).logout(anyString());
    }

    // GET /api/auth/me tests
    @Test
    @WithMockUser(username = "testuser")
    void getCurrentUser_Success_Returns200() throws Exception {
        // Given
        UserResponse userResponse = createTestUserResponse();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(authService.getCurrentUser(userId)).thenReturn(userResponse);

            // When/Then
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.username").value(username));

            verify(authService, times(1)).getCurrentUser(userId);
        }
    }

    @Test
    void getCurrentUser_Unauthenticated_Returns401() throws Exception {
        // When/Then - No @WithMockUser annotation
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getCurrentUser(any());
    }

    // POST /api/auth/api-keys tests
    @Test
    @WithMockUser(username = "testuser")
    void generateApiKey_Success_Returns201() throws Exception {
        // Given
        ApiKeyRequest request = new ApiKeyRequest("Test API Key", null);
        ApiKeyResponse response = createTestApiKeyResponse(true);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(apiKeyService.generateApiKey(any(ApiKeyRequest.class), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/auth/api-keys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.key").exists())
                    .andExpect(jsonPath("$.name").value("Test API Key"));

            verify(apiKeyService, times(1)).generateApiKey(any(ApiKeyRequest.class), eq(userId));
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateApiKey_MissingName_Returns400() throws Exception {
        // Given
        ApiKeyRequest request = new ApiKeyRequest();
        request.setName("");

        // When/Then
        mockMvc.perform(post("/api/auth/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(apiKeyService, never()).generateApiKey(any(), any());
    }

    @Test
    void generateApiKey_Unauthenticated_Returns401() throws Exception {
        // Given
        ApiKeyRequest request = new ApiKeyRequest("Test API Key", null);

        // When/Then
        mockMvc.perform(post("/api/auth/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(apiKeyService, never()).generateApiKey(any(), any());
    }

    // GET /api/auth/api-keys tests
    @Test
    @WithMockUser(username = "testuser")
    void listApiKeys_Success_Returns200() throws Exception {
        // Given
        List<ApiKeyResponse> responses = new ArrayList<>();
        responses.add(createTestApiKeyResponse(false));
        responses.add(createTestApiKeyResponse(false));

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(apiKeyService.listApiKeys(userId)).thenReturn(responses);

            // When/Then
            mockMvc.perform(get("/api/auth/api-keys"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].key").doesNotExist());

            verify(apiKeyService, times(1)).listApiKeys(userId);
        }
    }

    @Test
    void listApiKeys_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/auth/api-keys"))
                .andExpect(status().isUnauthorized());

        verify(apiKeyService, never()).listApiKeys(any());
    }

    // GET /api/auth/api-keys/{id} tests
    @Test
    @WithMockUser(username = "testuser")
    void getApiKey_Success_Returns200() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();
        ApiKeyResponse response = createTestApiKeyResponse(false);
        response.setId(apiKeyId);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(apiKeyService.getApiKey(apiKeyId, userId)).thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/auth/api-keys/{id}", apiKeyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(apiKeyId.toString()))
                    .andExpect(jsonPath("$.key").doesNotExist());

            verify(apiKeyService, times(1)).getApiKey(apiKeyId, userId);
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKey_NotFound_Returns404() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(apiKeyService.getApiKey(apiKeyId, userId))
                    .thenThrow(new ResourceNotFoundException("API key not found: " + apiKeyId, apiKeyId));

            // When/Then
            mockMvc.perform(get("/api/auth/api-keys/{id}", apiKeyId))
                    .andExpect(status().isNotFound());

            verify(apiKeyService, times(1)).getApiKey(apiKeyId, userId);
        }
    }

    @Test
    void getApiKey_Unauthenticated_Returns401() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();

        // When/Then
        mockMvc.perform(get("/api/auth/api-keys/{id}", apiKeyId))
                .andExpect(status().isUnauthorized());

        verify(apiKeyService, never()).getApiKey(any(), any());
    }

    // DELETE /api/auth/api-keys/{id} tests
    @Test
    @WithMockUser(username = "testuser")
    void revokeApiKey_Success_Returns204() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doNothing().when(apiKeyService).revokeApiKey(apiKeyId, userId);

            // When/Then
            mockMvc.perform(delete("/api/auth/api-keys/{id}", apiKeyId))
                    .andExpect(status().isNoContent());

            verify(apiKeyService, times(1)).revokeApiKey(apiKeyId, userId);
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    void revokeApiKey_NotFound_Returns404() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doThrow(new ResourceNotFoundException("API key not found: " + apiKeyId, apiKeyId))
                    .when(apiKeyService).revokeApiKey(apiKeyId, userId);

            // When/Then
            mockMvc.perform(delete("/api/auth/api-keys/{id}", apiKeyId))
                    .andExpect(status().isNotFound());

            verify(apiKeyService, times(1)).revokeApiKey(apiKeyId, userId);
        }
    }

    @Test
    void revokeApiKey_Unauthenticated_Returns401() throws Exception {
        // Given
        UUID apiKeyId = UUID.randomUUID();

        // When/Then
        mockMvc.perform(delete("/api/auth/api-keys/{id}", apiKeyId))
                .andExpect(status().isUnauthorized());

        verify(apiKeyService, never()).revokeApiKey(any(), any());
    }

    // Helper methods
    private AuthResponse createTestAuthResponse(String accessToken, String refreshToken, String clientType) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(900000L);
        response.setRefreshExpiresIn(604800000L);
        response.setClientType(clientType);
        response.setUser(createTestUserResponse());
        return response;
    }

    private UserResponse createTestUserResponse() {
        UserResponse response = new UserResponse();
        response.setId(userId);
        response.setUsername(username);
        response.setEmail(email);
        response.setActive(true);
        response.setCreatedAt(Instant.now());
        return response;
    }

    private ApiKeyResponse createTestApiKeyResponse(boolean includeKey) {
        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(UUID.randomUUID());
        if (includeKey) {
            response.setKey("test-api-key-1234567890123456");
        }
        response.setName("Test API Key");
        response.setActive(true);
        response.setCreatedAt(Instant.now());
        return response;
    }
}
