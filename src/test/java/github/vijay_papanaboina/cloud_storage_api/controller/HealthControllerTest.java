package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void health_Returns200() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void health_ReturnsCorrectStructure() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString()); // Instant is serialized as ISO-8601 string
    }

    @Test
    void health_NoAuthenticationRequired() throws Exception {
        // When/Then - No authentication needed, should work without @WithMockUser
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
