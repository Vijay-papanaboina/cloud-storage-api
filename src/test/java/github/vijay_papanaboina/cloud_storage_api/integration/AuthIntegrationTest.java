package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTest extends BaseIntegrationTest {

    @Test
    void registerNewUser_ShouldSaveInDatabase() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        // When
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.id").exists());

        // Then - verify user is saved in database
        Optional<User> savedUser = userRepository.findByUsername("testuser");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.get().getActive()).isTrue();
    }

    @Test
    void loginWithValidCredentials_ShouldReturnValidJWT() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        LoginRequest request = new LoginRequest("testuser", "password123");

        // When & Then
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify JWT is valid
        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        JwtTokenProvider jwtTokenProvider = this.jwtTokenProvider;
        assertThat(jwtTokenProvider.validateToken(authResponse.getAccessToken())).isTrue();
        try {
            assertThat(jwtTokenProvider.getUserIdFromToken(authResponse.getAccessToken())).isEqualTo(user.getId());
            assertThat(jwtTokenProvider.getUsernameFromToken(authResponse.getAccessToken())).isEqualTo("testuser");
        } catch (InvalidTokenException e) {
            throw new AssertionError("JWT token should be valid", e);
        }
    }

    @Test
    void loginWithInvalidCredentials_ShouldReturn401() throws Exception {
        // Given
        createTestUser("testuser", "test@example.com");
        LoginRequest request = new LoginRequest("testuser", "wrongpassword");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ShouldGenerateNewAccessToken() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        // When & Then
        String response = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify new access token is valid
        RefreshTokenResponse refreshResponse = objectMapper.readValue(response, RefreshTokenResponse.class);
        assertThat(jwtTokenProvider.validateToken(refreshResponse.getAccessToken())).isTrue();
        try {
            assertThat(jwtTokenProvider.getUserIdFromToken(refreshResponse.getAccessToken())).isEqualTo(user.getId());
        } catch (InvalidTokenException e) {
            throw new AssertionError("New access token should be valid", e);
        }
    }

    @Test
    void logout_ShouldSucceed() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        // When & Then - logout requires authentication (access token in header)
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void getCurrentUser_ShouldReturnUserDetails() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);

        // When & Then
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void registerWithDuplicateUsername_ShouldReturn409() throws Exception {
        // Given
        createTestUser("testuser", "test@example.com");
        RegisterRequest request = new RegisterRequest("testuser", "another@example.com", "password123");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithDuplicateEmail_ShouldReturn409() throws Exception {
        // Given
        createTestUser("testuser", "test@example.com");
        RegisterRequest request = new RegisterRequest("anotheruser", "test@example.com", "password123");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
