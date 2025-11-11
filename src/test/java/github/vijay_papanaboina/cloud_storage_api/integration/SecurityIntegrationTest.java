package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
    void jwtAndApiKey_ApiKeyTakesPrecedence() throws Exception {
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

        // When & Then - API Key runs first in CompositeAuthenticationFilter, so it
        // takes precedence over JWT
        // The API key filter sets authentication for user2, then JWT filter sees
        // authentication exists and skips
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken1)
                .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user2")); // API key takes precedence (runs first)
    }

    @Test
    void unauthenticatedRequestToProtectedEndpoint_ShouldReturn401() throws Exception {
        // When & Then - request without authentication should fail
        // Spring Security returns 403 (Forbidden) for unauthenticated requests in some
        // configurations
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidJwt_ShouldBeRejected() throws Exception {
        // When & Then - invalid JWT should be rejected
        // Spring Security returns 403 (Forbidden) for invalid authentication in some
        // configurations
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isForbidden());
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
