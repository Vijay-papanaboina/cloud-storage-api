package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.*;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.BatchJobRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import github.vijay_papanaboina.cloud_storage_api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileServiceImpl focusing on error contracts and edge cases.
 * Success scenarios are covered by integration tests.
 * These tests verify what exceptions are thrown, not how the code works
 * internally.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

        @Mock
        private FileRepository fileRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private StorageService storageService;

        @Mock
        private BatchJobRepository batchJobRepository;

        @InjectMocks
        private FileServiceImpl fileService;

        private UUID userId;
        private UUID fileId;
        private User testUser;
        private File testFile;
        private String folderPath;

        @BeforeEach
        void setUp() {
                userId = UUID.randomUUID();
                fileId = UUID.randomUUID();
                testUser = createTestUser(userId);
                testFile = createTestFile(fileId, userId, "test.txt");
                folderPath = "/documents";
        }

        // ==================== Upload Error Contract Tests ====================

        @Test
        void upload_UserNotFound_ThrowsNotFoundException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("User not found");
        }

        @Test
        void upload_NullUserId_ThrowsIllegalArgumentException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("User ID cannot be null");
        }

        @ParameterizedTest
        @Tag("error-contract")
        @ValueSource(strings = { "/../etc", "\\windows\\path", "documents" })
        void upload_InvalidFolderPath_ThrowsBadRequestException(String invalidPath) {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.of(invalidPath), Optional.empty(),
                                userId))
                                .isInstanceOf(BadRequestException.class);
        }

        @ParameterizedTest
        @Tag("error-contract")
        @ValueSource(strings = { "../malicious.txt", "", "CON.txt", "PRN", "folder/test.txt" })
        void upload_InvalidFilename_ThrowsBadRequestException(String invalidFilename) {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(),
                                Optional.of(invalidFilename), userId))
                                .isInstanceOf(BadRequestException.class);
        }

        @Test
        void upload_NullOriginalFilename_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = new MockMultipartFile(
                                "file",
                                null, // null filename
                                "text/plain",
                                "Test file content".getBytes());
                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("File original filename cannot be null or empty");
        }

        // ==================== Download Error Contract Tests ====================

        @Test
        void download_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.download(fileId, userId))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("File with ID");
        }

        // ==================== GetById Error Contract Tests ====================

        @Test
        void getById_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void getById_BelongsToAnotherUser_ThrowsAccessDeniedException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, otherUserId))
                                .isInstanceOf(AccessDeniedException.class)
                                .hasMessageContaining("Access denied");
        }

        @Test
        void getById_NullUserId_ThrowsIllegalArgumentException() {
                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");
        }

        // ==================== List Error Contract Tests ====================

        @Test
        void list_InvalidFolderPath_PathTraversal_ThrowsBadRequestException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "/../etc"; // Path traversal attempt

                // When/Then
                assertThatThrownBy(() -> fileService.list(pageable, Optional.empty(), Optional.of(invalidPath), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("path traversal");
        }

        @Test
        void list_InvalidFolderPath_Backslash_ThrowsBadRequestException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "\\windows\\path"; // Windows-style path

                // When/Then
                assertThatThrownBy(() -> fileService.list(pageable, Optional.empty(), Optional.of(invalidPath), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Unix-style");
        }

        @Test
        void list_InvalidFolderPath_NoLeadingSlash_ThrowsBadRequestException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "documents"; // Missing leading slash

                // When/Then
                assertThatThrownBy(() -> fileService.list(pageable, Optional.empty(), Optional.of(invalidPath), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("start with '/'");
        }

        // ==================== Update Error Contract Tests ====================

        @Test
        void update_InvalidFolderPath_PathTraversal_ThrowsBadRequestException() {
                // Given
                String invalidPath = "/../etc"; // Path traversal attempt
                FileUpdateRequest request = createTestFileUpdateRequest(null, invalidPath);
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("path traversal");
        }

        @Test
        void update_InvalidFolderPath_Backslash_ThrowsBadRequestException() {
                // Given
                String invalidPath = "\\windows\\path"; // Windows-style path
                FileUpdateRequest request = createTestFileUpdateRequest(null, invalidPath);
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Unix-style");
        }

        @Test
        void update_InvalidFolderPath_NoLeadingSlash_ThrowsBadRequestException() {
                // Given
                String invalidPath = "documents"; // Missing leading slash
                FileUpdateRequest request = createTestFileUpdateRequest(null, invalidPath);
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("start with '/'");
        }

        @Test
        void update_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void update_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, otherUserId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        // ==================== Delete Error Contract Tests ====================

        @Test
        void delete_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.delete(fileId, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void delete_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.delete(fileId, otherUserId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        // ==================== Search Error Contract Tests ====================

        @Test
        void search_InvalidFolderPath_PathTraversal_ThrowsBadRequestException() {
                // Given
                String query = "test";
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "/../etc"; // Path traversal attempt

                // When/Then
                assertThatThrownBy(
                                () -> fileService.search(query, Optional.empty(), Optional.of(invalidPath), pageable,
                                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("path traversal");
        }

        @Test
        void search_InvalidFolderPath_Backslash_ThrowsBadRequestException() {
                // Given
                String query = "test";
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "\\windows\\path"; // Windows-style path

                // When/Then
                assertThatThrownBy(
                                () -> fileService.search(query, Optional.empty(), Optional.of(invalidPath), pageable,
                                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Unix-style");
        }

        @Test
        void search_InvalidFolderPath_NoLeadingSlash_ThrowsBadRequestException() {
                // Given
                String query = "test";
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "documents"; // Missing leading slash

                // When/Then
                assertThatThrownBy(
                                () -> fileService.search(query, Optional.empty(), Optional.of(invalidPath), pageable,
                                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("start with '/'");
        }

        // ==================== Transform Error Contract Tests ====================

        @Test
        void transform_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                TransformRequest request = createTestTransformRequest(800, 600);
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.transform(fileId, request, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void transform_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                TransformRequest request = createTestTransformRequest(800, 600);
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.transform(fileId, request, otherUserId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void transform_InvalidTransformRequest_ThrowsBadRequestException() {
                // Given - Test with unsupported content type (PDF doesn't support
                // transformations)
                TransformRequest request = createTestTransformRequest(800, 600);
                File pdfFile = createTestFile(fileId, userId, "test.pdf");
                pdfFile.setContentType("application/pdf");
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(pdfFile));

                // When/Then
                assertThatThrownBy(() -> fileService.transform(fileId, request, userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("does not support");
        }

        // ==================== GetTransformUrl Error Contract Tests
        // ====================

        @Test
        void getTransformUrl_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getTransformUrl(fileId, 800, 600, Optional.empty(),
                                Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void getTransformUrl_BelongsToAnotherUser_ThrowsAccessDeniedException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.getTransformUrl(fileId, 800, 600, Optional.empty(),
                                Optional.empty(), Optional.empty(), otherUserId))
                                .isInstanceOf(AccessDeniedException.class)
                                .hasMessageContaining("Access denied");
        }

        // ==================== GetSignedDownloadUrl Error Contract Tests
        // ====================

        @Test
        void getSignedDownloadUrl_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getSignedDownloadUrl(fileId, userId, 60))
                                .isInstanceOf(CloudFileNotFoundException.class);
        }

        @Test
        void getSignedDownloadUrl_BelongsToAnotherUser_ThrowsAccessDeniedException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findById(fileId)).thenReturn(Optional.of(testFile));

                // When/Then
                assertThatThrownBy(() -> fileService.getSignedDownloadUrl(fileId, otherUserId, 60))
                                .isInstanceOf(AccessDeniedException.class)
                                .hasMessageContaining("Access denied");
        }

        // ==================== Helper Methods ====================

        private User createTestUser(UUID userId) {
                User user = new User();
                user.setId(userId);
                user.setUsername("testuser");
                user.setEmail("test@example.com");
                user.setPasswordHash("$2a$10$hashedPassword");
                user.setActive(true);
                user.setCreatedAt(Instant.now());
                return user;
        }

        private File createTestFile(UUID fileId, UUID userId, String filename) {
                File file = new File();
                file.setId(fileId);
                file.setUser(testUser);
                file.setFilename(filename);
                file.setContentType("text/plain");
                file.setFileSize(1024L);
                file.setFolderPath(folderPath);
                file.setCloudinaryPublicId("test-public-id");
                file.setCloudinaryUrl("http://res.cloudinary.com/test/image/upload/test.jpg");
                file.setCloudinarySecureUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
                file.setDeleted(false);
                file.setCreatedAt(Instant.now());
                file.setUpdatedAt(Instant.now());
                return file;
        }

        private MockMultipartFile createTestMultipartFile(String filename) {
                return new MockMultipartFile(
                                "file",
                                filename,
                                "text/plain",
                                "Test file content".getBytes());
        }

        private FileUpdateRequest createTestFileUpdateRequest(String filename, String folderPath) {
                FileUpdateRequest request = new FileUpdateRequest();
                request.setFilename(filename);
                request.setFolderPath(folderPath);
                return request;
        }

        private TransformRequest createTestTransformRequest(Integer width, Integer height) {
                TransformRequest request = new TransformRequest();
                request.setWidth(width);
                request.setHeight(height);
                request.setCrop("fill");
                request.setQuality("auto");
                request.setFormat("webp");
                return request;
        }
}
