package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for service error contract tests.
 * Provides common setup and helper methods for service unit tests.
 */
public abstract class BaseServiceErrorContractTest {

    @Mock
    protected User testUser;

    protected UUID userId;
    protected UUID fileId;

    @BeforeEach
    void baseSetUp() {
        userId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        testUser = createTestUser(userId);
    }

    /**
     * Creates a test user with the given ID.
     */
    protected User createTestUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    /**
     * Creates a test file with the given ID, user ID, and filename.
     */
    protected File createTestFile(UUID fileId, UUID userId, String filename) {
        File file = new File();
        file.setId(fileId);
        file.setUser(createTestUser(userId));
        file.setFilename(filename);
        file.setFolderPath(null);
        file.setFileSize(1024L);
        file.setContentType("text/plain");
        file.setCloudinaryPublicId("public-id-" + fileId);
        file.setCloudinaryUrl("https://cloudinary.com/test.jpg");
        file.setCloudinarySecureUrl("https://cloudinary.com/test.jpg");
        file.setDeleted(false);
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        return file;
    }
}

