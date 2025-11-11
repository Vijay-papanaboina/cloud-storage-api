package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiKeyIntegrationTest extends BaseIntegrationTest {

        @Test
        void generateApiKey_ShouldSaveInDatabase() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                ApiKeyRequest request = new ApiKeyRequest("Test API Key", null);

                // When
                String response = mockMvc.perform(post("/api/auth/api-keys")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.key").exists())
                                .andExpect(jsonPath("$.name").value("Test API Key"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                // Then - verify API key is saved in database
                ApiKeyResponse apiKeyResponse = objectMapper.readValue(response, ApiKeyResponse.class);
                Optional<ApiKey> savedApiKey = apiKeyRepository.findById(apiKeyResponse.getId());
                assertThat(savedApiKey).isPresent();
                assertThat(savedApiKey.get().getName()).isEqualTo("Test API Key");
                assertThat(savedApiKey.get().getKey()).hasSize(32); // API key should be 32 characters
                assertThat(savedApiKey.get().getActive()).isTrue();
                assertThat(savedApiKey.get().getUser().getId()).isEqualTo(user.getId());
        }

        @Test
        void listApiKeys_ShouldReturnUserApiKeys() throws Exception {
                // Given
                User user1 = createTestUser("user1", "user1@example.com");
                User user2 = createTestUser("user2", "user2@example.com");
                String accessToken1 = generateAccessToken(user1);

                // Create API keys for both users
                ApiKeyRequest request1 = new ApiKeyRequest("User1 Key 1", null);
                ApiKeyRequest request2 = new ApiKeyRequest("User1 Key 2", null);

                mockMvc.perform(post("/api/auth/api-keys")
                                .header("Authorization", "Bearer " + accessToken1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/auth/api-keys")
                                .header("Authorization", "Bearer " + accessToken1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request2)))
                                .andExpect(status().isCreated());

                // When & Then
                mockMvc.perform(get("/api/auth/api-keys")
                                .header("Authorization", "Bearer " + accessToken1))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(2));

                // Verify user isolation - user2 should have no API keys
                List<ApiKey> user2Keys = apiKeyRepository.findByUserId(user2.getId());
                assertThat(user2Keys).isEmpty();
        }

        @Test
        void getApiKeyById_ShouldReturnApiKey() throws Exception {
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

                // When & Then
                mockMvc.perform(get("/api/auth/api-keys/" + created.getId())
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                                .andExpect(jsonPath("$.name").value("Test Key"))
                                .andExpect(jsonPath("$.key").doesNotExist()); // Key should not be returned on GET
        }

        @Test
        void revokeApiKey_ShouldMarkInactive() throws Exception {
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

                // When
                mockMvc.perform(delete("/api/auth/api-keys/" + created.getId())
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isNoContent());

                // Then - verify API key is marked inactive in database
                Optional<ApiKey> revokedKey = apiKeyRepository.findById(created.getId());
                assertThat(revokedKey).isPresent();
                assertThat(revokedKey.get().getActive()).isFalse();
        }

        @Test
        void authenticateWithApiKey_ShouldSucceed() throws Exception {
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

                // When & Then - use API key to access protected endpoint
                mockMvc.perform(get("/api/auth/me")
                                .header("X-API-Key", apiKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value("testuser"));
        }

        @Test
        void authenticateWithExpiredApiKey_ShouldReturn401() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                ApiKeyRequest request = new ApiKeyRequest("Test Key", null); // Create with no expiration

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

                // Manually expire the API key in the database
                Optional<ApiKey> apiKeyEntity = apiKeyRepository.findById(created.getId());
                assertThat(apiKeyEntity).isPresent();
                apiKeyEntity.get().setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
                apiKeyRepository.save(apiKeyEntity.get());

                // When & Then - expired API key should not authenticate (Spring Security
                // returns 403 for failed authentication)
                mockMvc.perform(get("/api/auth/me")
                                .header("X-API-Key", apiKey))
                                .andExpect(status().isForbidden());
        }

        @Test
        void getApiKeyBelongingToAnotherUser_ShouldReturn404() throws Exception {
                // Given
                User user1 = createTestUser("user1", "user1@example.com");
                User user2 = createTestUser("user2", "user2@example.com");
                String accessToken1 = generateAccessToken(user1);
                String accessToken2 = generateAccessToken(user2);

                ApiKeyRequest request = new ApiKeyRequest("User1 Key", null);
                String createResponse = mockMvc.perform(post("/api/auth/api-keys")
                                .header("Authorization", "Bearer " + accessToken1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                ApiKeyResponse created = objectMapper.readValue(createResponse, ApiKeyResponse.class);

                // When & Then - user2 should not be able to access user1's API key
                mockMvc.perform(get("/api/auth/api-keys/" + created.getId())
                                .header("Authorization", "Bearer " + accessToken2))
                                .andExpect(status().isNotFound());
        }
}
