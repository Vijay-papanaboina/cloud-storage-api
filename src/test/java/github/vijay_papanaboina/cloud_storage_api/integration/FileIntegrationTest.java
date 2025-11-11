package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.dto.FileResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FileUpdateRequest;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

                when(storageService.uploadFile(any(), eq(""), any())).thenReturn(cloudinaryResponse);

                // When
                MvcResult result = mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.filename").value("test.txt"))
                                .andExpect(jsonPath("$.contentType").value("text/plain"))
                                .andReturn();

                // Then - verify file is saved in database
                FileResponse response = objectMapper.readValue(
                                result.getResponse().getContentAsString(), FileResponse.class);
                Optional<File> savedFile = fileRepository.findById(response.getId());
                assertThat(savedFile).isPresent();
                assertThat(savedFile.get().getFilename()).isEqualTo("test.txt");
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
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content.length()").value(3))
                                .andExpect(jsonPath("$.totalElements").value(3));
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

                when(storageService.uploadFile(any(), eq("/documents"), any())).thenReturn(cloudinaryResponse);

                // When & Then - valid folder path
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "/documents")
                                .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isCreated());

                // When & Then - invalid folder path (path traversal)
                mockMvc.perform(multipart("/api/files/upload")
                                .file(multipartFile)
                                .param("folderPath", "/../etc")
                                .header("Authorization", "Bearer " + accessToken))
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
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(10))
                                .andExpect(jsonPath("$.totalElements").value(15))
                                .andExpect(jsonPath("$.totalPages").value(2));

                // When & Then - second page
                mockMvc.perform(get("/api/files")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(5))
                                .andExpect(jsonPath("$.totalElements").value(15));
        }
}
