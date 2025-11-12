package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.FileResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FileUpdateRequest;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FileIntegrationTest extends BaseIntegrationTest {

        private File createTestFileInDatabase(User user, String filename, String folderPath) {
                File file = new File();
                file.setUser(user);
                file.setFilename(filename);
                file.setContentType("text/plain");
                file.setFileSize(1024L);
                file.setFolderPath(folderPath);
                file.setCloudinaryPublicId("test-public-id-" + UUID.randomUUID());
                file.setCloudinaryUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
                file.setCloudinarySecureUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
                file.setDeleted(false);
                return fileRepository.save(file);
        }

        @Test
        void uploadFile_ShouldSaveInDatabase() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", "test.txt", "text/plain", "test content".getBytes());

                Map<String, Object> cloudinaryResponse = new HashMap<>();
                cloudinaryResponse.put("public_id", "test-public-id");
                cloudinaryResponse.put("url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                cloudinaryResponse.put("secure_url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                cloudinaryResponse.put("bytes", 1024L);

                when(storageService.uploadFile(any(), eq(user.getId().toString()), any()))
                                .thenReturn(cloudinaryResponse);

                // When
                MvcResult result = mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.filename").exists()) // Filename is UUID-based
                                .andExpect(jsonPath("$.contentType").value("text/plain"))
                                .andReturn();

                // Then - verify file is saved in database
                FileResponse response = objectMapper.readValue(
                                result.getResponse().getContentAsString(), FileResponse.class);
                // Verify filename is preserved (implementation now preserves original filename
                // instead of UUID)
                assertThat(response.getFilename()).isEqualTo("test.txt");

                Optional<File> savedFile = fileRepository.findById(response.getId());
                assertThat(savedFile).isPresent();
                assertThat(savedFile.get().getFilename()).isEqualTo("test.txt"); // Original filename is preserved
                assertThat(savedFile.get().getUser().getId()).isEqualTo(user.getId());
        }

        @Test
        void listFiles_ShouldReturnPaginatedResults() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                createTestFileInDatabase(user, "file1.txt", null);
                createTestFileInDatabase(user, "file2.txt", null);
                createTestFileInDatabase(user, "file3.txt", null);

                // When & Then
                // With PageSerializationMode.VIA_DTO, content stays at root but metadata is in
                // page object
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content.length()").value(3))
                                .andExpect(jsonPath("$.page.totalElements").value(3));
        }

        @Test
        void getFileById_ShouldReturnFileMetadata() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                File file = createTestFileInDatabase(user, "test.txt", null);

                // When & Then
                mockMvc.perform(get("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(file.getId().toString()))
                                .andExpect(jsonPath("$.filename").value("test.txt"))
                                .andExpect(jsonPath("$.contentType").value("text/plain"));
        }

        @Test
        void updateFileMetadata_ShouldPersistChanges() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                File file = createTestFileInDatabase(user, "oldname.txt", null);
                FileUpdateRequest updateRequest = new FileUpdateRequest("newname.txt", "/documents");

                // Mock storage service calls for folder move
                // File has no folderPath (null), so expected path is userId/publicId
                String currentFullPath = user.getId() + "/" + file.getCloudinaryPublicId();
                String newCloudinaryPath = user.getId() + "/documents";

                Map<String, Object> moveResult = new HashMap<>();
                moveResult.put("public_id", file.getCloudinaryPublicId());
                moveResult.put("url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                moveResult.put("secure_url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                // moveFile now accepts null resourceType and handles resource type detection
                // internally
                when(storageService.moveFile(eq(currentFullPath), eq(newCloudinaryPath), isNull()))
                                .thenReturn(moveResult);

                // When
                mockMvc.perform(put("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.filename").value("newname.txt"))
                                .andExpect(jsonPath("$.folderPath").value("/documents"));

                // Then - verify changes persisted in database
                Optional<File> updatedFile = fileRepository.findById(file.getId());
                assertThat(updatedFile).isPresent();
                assertThat(updatedFile.get().getFilename()).isEqualTo("newname.txt");
                assertThat(updatedFile.get().getFolderPath()).isEqualTo("/documents");
        }

        @Test
        void deleteFile_ShouldMarkAsDeleted() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                File file = createTestFileInDatabase(user, "test.txt", null);

                when(storageService.deleteFile(any())).thenReturn(true);

                // When
                mockMvc.perform(delete("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isNoContent());

                // Then - verify file is marked as deleted in database
                Optional<File> deletedFile = fileRepository.findById(file.getId());
                assertThat(deletedFile).isPresent();
                assertThat(deletedFile.get().getDeleted()).isTrue();
        }

        @Test
        void searchFiles_ShouldReturnMatchingResults() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                createTestFileInDatabase(user, "document1.txt", null);
                createTestFileInDatabase(user, "document2.txt", null);
                createTestFileInDatabase(user, "image.jpg", null);

                // When & Then
                mockMvc.perform(get("/api/files/search")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("q", "document"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void getFileStatistics_ShouldReturnCorrectCalculations() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                createTestFileInDatabase(user, "file1.txt", null);
                createTestFileInDatabase(user, "file2.txt", null);

                // When & Then
                mockMvc.perform(get("/api/files/statistics")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalFiles").value(2))
                                .andExpect(jsonPath("$.totalSize").value(2048));
        }

        @Test
        void fileOperationsWithFolderPath_ShouldValidatePath() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", "test.txt", "text/plain", "test content".getBytes());

                Map<String, Object> cloudinaryResponse = new HashMap<>();
                cloudinaryResponse.put("public_id", "test-public-id");
                cloudinaryResponse.put("url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                cloudinaryResponse.put("secure_url", "https://res.cloudinary.com/test/image/upload/test.jpg");
                cloudinaryResponse.put("bytes", 1024L);

                String expectedCloudinaryPath = user.getId() + "/documents";
                when(storageService.uploadFile(any(), eq(expectedCloudinaryPath), any()))
                                .thenReturn(cloudinaryResponse);

                // When & Then - valid folder path
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "/documents")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isCreated());

                // When & Then - invalid folder path (path traversal)
                // FileServiceImpl now uses SafeFolderPathValidator which rejects path traversal
                // attempts, so this should return 400 Bad Request instead of 503
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "/../etc")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest()); // Path traversal is now caught by validator

                // When & Then - invalid folder path (backslash)
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "\\windows\\path")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());

                // When & Then - invalid folder path (no leading slash)
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "documents")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void uploadFile_InvalidFilename_WithPathSeparator_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", "test.txt", "text/plain", "test content".getBytes());

                // When & Then - custom filename with path separator
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("filename", "../malicious.txt")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void uploadFile_InvalidFilename_Empty_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", "test.txt", "text/plain", "test content".getBytes());

                // When & Then - empty custom filename
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("filename", "")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void uploadFile_InvalidFilename_ReservedName_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", "test.txt", "text/plain", "test content".getBytes());

                // When & Then - Windows reserved name
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("filename", "CON.txt")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void uploadFile_NullOriginalFilename_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                MockMultipartFile multipartFile = new MockMultipartFile(
                                "file", null, "text/plain", "test content".getBytes());

                // When & Then - null original filename
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void updateFile_InvalidFolderPath_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                File file = createTestFileInDatabase(user, "test.txt", "/documents");

                // When & Then - path traversal in update
                FileUpdateRequest updateRequest = new FileUpdateRequest(null, "/../etc");
                mockMvc.perform(put("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest());

                // When & Then - backslash in update
                updateRequest = new FileUpdateRequest(null, "\\windows\\path");
                mockMvc.perform(put("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void listFiles_InvalidFolderPath_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);

                // When & Then - path traversal in list
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("folderPath", "/../etc"))
                                .andExpect(status().isBadRequest());

                // When & Then - backslash in list
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("folderPath", "\\windows\\path"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void searchFiles_InvalidFolderPath_ShouldReturn400() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);

                // When & Then - path traversal in search
                mockMvc.perform(get("/api/files/search")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("q", "test")
                                .param("folderPath", "/../etc"))
                                .andExpect(status().isBadRequest());

                // When & Then - backslash in search
                mockMvc.perform(get("/api/files/search")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("q", "test")
                                .param("folderPath", "\\windows\\path"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void userIsolation_ShouldPreventAccessToOtherUsersFiles() throws Exception {
                // Given
                User user1 = createTestUser("user1", "user1@example.com");
                User user2 = createTestUser("user2", "user2@example.com");
                String accessToken2 = generateAccessToken(user2);
                File file = createTestFileInDatabase(user1, "user1file.txt", null);

                // When & Then - user2 should not be able to access user1's file
                mockMvc.perform(get("/api/files/" + file.getId())
                                .header("Authorization", "Bearer " + accessToken2))
                                .andExpect(status().isNotFound());
        }

        @Test
        void listFilesWithPagination_ShouldReturnCorrectPage() throws Exception {
                // Given
                User user = createTestUser("testuser", "test@example.com");
                String accessToken = generateAccessToken(user);
                for (int i = 1; i <= 15; i++) {
                        createTestFileInDatabase(user, "file" + i + ".txt", null);
                }

                // When & Then - first page
                // With PageSerializationMode.VIA_DTO, content stays at root but metadata is in
                // page object
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(10))
                                .andExpect(jsonPath("$.page.totalElements").value(15))
                                .andExpect(jsonPath("$.page.totalPages").value(2));

                // When & Then - second page
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(5))
                                .andExpect(jsonPath("$.page.totalElements").value(15));
        }
}
