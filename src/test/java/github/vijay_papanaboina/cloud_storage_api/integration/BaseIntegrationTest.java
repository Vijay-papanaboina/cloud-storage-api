package github.vijay_papanaboina.cloud_storage_api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.vijay_papanaboina.cloud_storage_api.model.ClientType;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.ApiKeyRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.BatchJobRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import github.vijay_papanaboina.cloud_storage_api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected StorageService storageService; // Mock Cloudinary

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected FileRepository fileRepository;

    @Autowired
    protected ApiKeyRepository apiKeyRepository;

    @Autowired
    protected BatchJobRepository batchJobRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        batchJobRepository.deleteAll();
        fileRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Helper methods for common test operations
    protected User createTestUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setActive(true);
        return userRepository.save(user);
    }

    protected String generateAccessToken(User user) {
        return jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                ClientType.WEB);
    }
}
