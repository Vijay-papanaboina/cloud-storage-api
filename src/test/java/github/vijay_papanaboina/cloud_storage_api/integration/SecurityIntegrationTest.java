package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    void jwtAuthentication_ShouldWork() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);

        // When & Then - JWT should authenticate request
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void apiKeyAuthentication_ShouldWork() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        ApiKeyRequest request = new ApiKeyRequest("Test Key", null);

        String createResponse = mockMvc.perform(post("/api/auth/api-keys")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiKeyResponse created = objectMapper.readValue(createResponse, ApiKeyResponse.class);
        String apiKey = created.getKey();

        // When & Then - API Key should authenticate request
        mockMvc.perform(get("/api/auth/me")
                .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void jwtAndApiKey_JwtTakesPrecedence() throws Exception {
        // Given
        User user1 = createTestUser("user1", "user1@example.com");
        User user2 = createTestUser("user2", "user2@example.com");
        String accessToken1 = generateAccessToken(user1);
        String accessToken2 = generateAccessToken(user2);

        // Create API key for user2
        ApiKeyRequest request = new ApiKeyRequest("Test Key", null);
        String createResponse = mockMvc.perform(post("/api/auth/api-keys")
                .header("Authorization", "Bearer " + accessToken2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiKeyResponse created = objectMapper.readValue(createResponse, ApiKeyResponse.class);
        String apiKey = created.getKey();

        // When & Then - JWT should take precedence over API Key
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken1)
                .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user1")); // Should be user1 from JWT, not user2 from API key
    }

    @Test
    void unauthenticatedRequestToProtectedEndpoint_ShouldReturn401() throws Exception {
        // When & Then - request without authentication should fail
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwt_ShouldBeRejected() throws Exception {
        // When & Then - invalid JWT should be rejected
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsHeaders_ShouldBeConfigured() throws Exception {
        // When & Then - CORS headers should be present
        mockMvc.perform(options("/api/auth/me")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }
}
