package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderCreateRequest;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FolderIntegrationTest extends BaseIntegrationTest {

    private File createTestFileInDatabase(User user, String filename, String folderPath) {
        File file = new File();
        file.setUser(user);
        file.setFilename(filename);
        file.setContentType("text/plain");
        file.setFileSize(1024L);
        file.setFolderPath(folderPath);
        file.setCloudinaryPublicId("test-public-id-" + java.util.UUID.randomUUID());
        file.setCloudinaryUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
        file.setCloudinarySecureUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
        file.setDeleted(false);
        return fileRepository.save(file);
    }

    @Test
    void validateFolderPath_ShouldPreventPathTraversal() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        FolderCreateRequest validRequest = new FolderCreateRequest("/documents", "Valid folder");
        FolderCreateRequest invalidRequest = new FolderCreateRequest("/../etc", "Path traversal attempt");

        // When & Then - valid path (creates folder, returns 201)
        mockMvc.perform(post("/api/folders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // When & Then - invalid path (path traversal) should be rejected
        mockMvc.perform(post("/api/folders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFolders_ShouldReturnUserFolders() throws Exception {
        // Given
        User user1 = createTestUser("user1", "user1@example.com");
        User user2 = createTestUser("user2", "user2@example.com");
        String accessToken1 = generateAccessToken(user1);
        String accessToken2 = generateAccessToken(user2);

        // Create files in different folders for user1
        createTestFileInDatabase(user1, "file1.txt", "/documents");
        createTestFileInDatabase(user1, "file2.txt", "/documents");
        createTestFileInDatabase(user1, "file3.txt", "/images");

        // Create files for user2
        createTestFileInDatabase(user2, "file4.txt", "/videos");

        // When & Then - user1 should see their folders
        mockMvc.perform(get("/api/folders")
                .header("Authorization", "Bearer " + accessToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        // When & Then - user2 should see only their folders
        mockMvc.perform(get("/api/folders")
                .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getFolderStatistics_ShouldReturnCorrectCounts() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        createTestFileInDatabase(user, "file1.txt", "/documents");
        createTestFileInDatabase(user, "file2.txt", "/documents");
        createTestFileInDatabase(user, "file3.txt", "/documents");

        // When & Then
        mockMvc.perform(get("/api/folders/statistics")
                .header("Authorization", "Bearer " + accessToken)
                .param("path", "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(3))
                .andExpect(jsonPath("$.totalSize").value(3072));
    }

    @Test
    void deleteFolder_ShouldSucceedWhenEmpty() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        // Create a file in the folder first
        createTestFileInDatabase(user, "file1.txt", "/empty-folder");
        // Delete the file (mark as deleted) to make folder empty
        File file = fileRepository
                .findByUserIdAndDeletedFalse(user.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .filter(f -> f.getFolderPath() != null && f.getFolderPath().equals("/empty-folder"))
                .findFirst()
                .orElseThrow();
        file.setDeleted(true);
        fileRepository.save(file);

        // When & Then - empty folders (with no active files) return 404 (not found)
        // because folders are virtual and only exist when they have active files
        mockMvc.perform(delete("/api/folders")
                .header("Authorization", "Bearer " + accessToken)
                .param("path", "/empty-folder"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFolder_ShouldFailWhenNotEmpty() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        createTestFileInDatabase(user, "file1.txt", "/documents");

        // When & Then - folder with files should not be deletable
        mockMvc.perform(delete("/api/folders")
                .header("Authorization", "Bearer " + accessToken)
                .param("path", "/documents"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void folderPathValidationWithInvalidCharacters_ShouldReturn400() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        // Use a path that definitely fails validation (contains characters rejected by
        // SafeFolderPathValidator: < > : " | ? *)
        FolderCreateRequest invalidRequest = new FolderCreateRequest("/folder:with*invalid|chars",
                "Invalid characters");

        // When & Then - paths with invalid characters should be rejected
        mockMvc.perform(post("/api/folders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
