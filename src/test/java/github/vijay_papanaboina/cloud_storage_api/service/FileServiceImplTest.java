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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

        // upload tests

        @Test
        void upload_Success_WithoutFolderPath() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                Map<String, Object> cloudinaryResponse = createTestCloudinaryResponse("test-public-id");
                String expectedCloudinaryPath = getExpectedCloudinaryFolderPath(null);

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath), any()))
                                .thenReturn(cloudinaryResponse);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFilename()).isEqualTo("test.txt"); // Original filename should be preserved
                assertThat(response.getContentType()).isEqualTo("text/plain");
                assertThat(response.getFileSize()).isEqualTo(multipartFile.getSize()); // Use actual file size
                assertThat(response.getCloudinaryUrl()).isNotNull();
                assertThat(response.getCloudinarySecureUrl()).isNotNull();

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath),
                                any());
                verify(fileRepository, times(1)).save(any(File.class));
        }

        @Test
        void upload_Success_WithFolderPath() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                Map<String, Object> cloudinaryResponse = createTestCloudinaryResponse("test-public-id");
                String expectedCloudinaryPath = getExpectedCloudinaryFolderPath(folderPath);

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(), eq(expectedCloudinaryPath), any()))
                                .thenReturn(cloudinaryResponse);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.upload(multipartFile, Optional.of(folderPath), Optional.empty(),
                                userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFolderPath()).isEqualTo(folderPath);

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(), eq(expectedCloudinaryPath), any());
                verify(fileRepository, times(1)).save(any(File.class));
        }

        @Test
        void upload_UserNotFound_ThrowsNotFoundException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");

                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("User not found");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_NullUserId_ThrowsIllegalArgumentException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("User ID cannot be null");

                verify(userRepository, never()).findById(any());
                verify(storageService, never()).uploadFile(any(), any(), any());
        }

        @Test
        void upload_StorageFailure_ThrowsStorageException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(), any(), any()))
                                .thenThrow(new StorageException("Cloudinary upload failed"));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("Cloudinary upload failed");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFolderPath_PathTraversal_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidPath = "/../etc"; // Path traversal attempt

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.of(invalidPath), Optional.empty(),
                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("path traversal");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFolderPath_Backslash_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidPath = "\\windows\\path"; // Windows-style path

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.of(invalidPath), Optional.empty(),
                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Unix-style");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFolderPath_NoLeadingSlash_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidPath = "documents"; // Missing leading slash

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.of(invalidPath), Optional.empty(),
                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("start with '/'");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFolderPath_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidPath = "invalid-path"; // Doesn't start with "/"

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.of(invalidPath), Optional.empty(),
                                userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Folder path must start with '/'");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
        }

        @Test
        void upload_CloudinaryReturnsNull_ThrowsStorageException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(), any(), any())).thenReturn(null);

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("upload result is null");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_Success_WithCustomFilename() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("original.txt");
                String customFilename = "custom-filename.pdf";
                Map<String, Object> cloudinaryResponse = createTestCloudinaryResponse("test-public-id");
                String expectedCloudinaryPath = getExpectedCloudinaryFolderPath(null);

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath), any()))
                                .thenReturn(cloudinaryResponse);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.upload(multipartFile, Optional.empty(), Optional.of(customFilename),
                                userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFilename()).isEqualTo(customFilename); // Custom filename should be used
                assertThat(response.getContentType()).isEqualTo("text/plain");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath),
                                any());
                verify(fileRepository, times(1)).save(any(File.class));
        }

        @Test
        void upload_Success_PreservesOriginalFilename() {
                // Given
                String originalFilename = "my-document.pdf";
                MultipartFile multipartFile = createTestMultipartFile(originalFilename);
                Map<String, Object> cloudinaryResponse = createTestCloudinaryResponse("test-public-id");
                String expectedCloudinaryPath = getExpectedCloudinaryFolderPath(null);

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
                when(storageService.uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath), any()))
                                .thenReturn(cloudinaryResponse);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.upload(multipartFile, Optional.empty(), Optional.empty(), userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFilename()).isEqualTo(originalFilename); // Original filename should be preserved

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, times(1)).uploadFile(any(MultipartFile.class), eq(expectedCloudinaryPath),
                                any());
                verify(fileRepository, times(1)).save(any(File.class));
        }

        @Test
        void upload_InvalidFilename_WithPathSeparator_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidFilename = "../malicious.txt"; // Contains path separator

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(),
                                Optional.of(invalidFilename), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Filename cannot be");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFilename_Empty_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidFilename = ""; // Empty filename

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(),
                                Optional.of(invalidFilename), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Filename cannot be null or empty");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void upload_InvalidFilename_ReservedName_ThrowsBadRequestException() {
                // Given
                MultipartFile multipartFile = createTestMultipartFile("test.txt");
                String invalidFilename = "CON.txt"; // Windows reserved name

                when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

                // When/Then
                assertThatThrownBy(() -> fileService.upload(multipartFile, Optional.empty(),
                                Optional.of(invalidFilename), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("Filename cannot be a reserved name");

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
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

                verify(userRepository, times(1)).findById(userId);
                verify(storageService, never()).uploadFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        // download tests

        @Test
        void download_Success_ReturnsResource() {
                // Given
                byte[] fileBytes = "Test file content".getBytes();
                String expectedFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(storageService.downloadFile(eq(expectedFullPath))).thenReturn(fileBytes);

                // When
                Resource resource = fileService.download(fileId, userId);

                // Then
                assertThat(resource).isNotNull();
                assertThat(resource).isInstanceOf(ByteArrayResource.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).downloadFile(eq(expectedFullPath));
        }

        @Test
        void download_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.download(fileId, userId))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("File with ID");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).downloadFile(any());
        }

        @Test
        void download_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.download(fileId, otherUserId))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("File with ID");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
                verify(storageService, never()).downloadFile(any());
        }

        @Test
        void download_StorageFailure_ThrowsStorageException() {
                // Given
                String expectedFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(storageService.downloadFile(eq(expectedFullPath)))
                                .thenThrow(new StorageException("Download failed"));

                // When/Then
                assertThatThrownBy(() -> fileService.download(fileId, userId))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("Download failed");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).downloadFile(eq(expectedFullPath));
        }

        // getSignedDownloadUrl tests

        @Test
        void getSignedDownloadUrl_Success_ReturnsUrl() {
                // Given
                int expirationMinutes = 60;
                Map<String, Object> resourceDetails = new HashMap<>();
                resourceDetails.put("format", "jpg");
                resourceDetails.put("resource_type", "image");
                String signedUrl = "https://res.cloudinary.com/test/image/upload/signed-url.jpg";
                String expectedFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(storageService.getResourceDetails(eq(expectedFullPath))).thenReturn(resourceDetails);
                when(storageService.generateSignedDownloadUrl(eq(expectedFullPath), eq(expirationMinutes),
                                eq("image"))).thenReturn(signedUrl);

                // When
                FileUrlResponse response = fileService.getSignedDownloadUrl(fileId, userId, expirationMinutes);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getUrl()).isEqualTo(signedUrl);
                assertThat(response.getPublicId()).isEqualTo(testFile.getCloudinaryPublicId());
                assertThat(response.getFormat()).isEqualTo("jpg");
                assertThat(response.getResourceType()).isEqualTo("image");
                assertThat(response.getExpiresAt()).isNotNull();

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getResourceDetails(eq(expectedFullPath));
                verify(storageService, times(1)).generateSignedDownloadUrl(eq(expectedFullPath),
                                eq(expirationMinutes), eq("image"));
        }

        @Test
        void getSignedDownloadUrl_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getSignedDownloadUrl(fileId, userId, 60))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).getResourceDetails(any());
        }

        @Test
        void getSignedDownloadUrl_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getSignedDownloadUrl(fileId, otherUserId, 60))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
                verify(storageService, never()).getResourceDetails(any());
        }

        @Test
        void getSignedDownloadUrl_StorageFailure_ThrowsStorageException() {
                // Given
                String expectedFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(storageService.getResourceDetails(eq(expectedFullPath)))
                                .thenThrow(new StorageException("Storage error"));

                // When/Then
                assertThatThrownBy(() -> fileService.getSignedDownloadUrl(fileId, userId, 60))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("Storage error");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getResourceDetails(eq(expectedFullPath));
        }

        // getById tests

        @Test
        void getById_Success_ReturnsFileResponse() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));

                // When
                FileResponse response = fileService.getById(fileId, userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(fileId);
                assertThat(response.getFilename()).isEqualTo("test.txt");
                assertThat(response.getContentType()).isEqualTo("text/plain");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
        }

        @Test
        void getById_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
        }

        @Test
        void getById_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, otherUserId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
        }

        @Test
        void getById_NullUserId_ThrowsIllegalArgumentException() {
                // When/Then
                assertThatThrownBy(() -> fileService.getById(fileId, null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");

                // Verify repository is not invoked for null userId
                verify(fileRepository, never()).findByIdAndUserId(any(), any());
        }

        // list tests

        @Test
        void list_Success_AllFiles() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                List<File> files = Arrays.asList(testFile);
                Page<File> filePage = new PageImpl<>(files, pageable, 1);

                when(fileRepository.findByUserIdAndDeletedFalse(userId, pageable)).thenReturn(filePage);

                // When
                Page<FileResponse> result = fileService.list(pageable, Optional.empty(), Optional.empty(), userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getTotalElements()).isEqualTo(1);
                assertThat(result.getContent().get(0).getId()).isEqualTo(fileId);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalse(userId, pageable);
        }

        @Test
        void list_WithFilters_ReturnsFilteredResults() {
                // Given
                String contentType = "text/plain";
                Pageable pageable = PageRequest.of(0, 20);
                List<File> files = Arrays.asList(testFile);
                Page<File> filePage = new PageImpl<>(files, pageable, 1);

                when(fileRepository.findByUserIdAndDeletedFalseAndContentTypeAndFolderPath(userId, contentType,
                                folderPath,
                                pageable)).thenReturn(filePage);

                // When
                Page<FileResponse> result = fileService.list(pageable, Optional.of(contentType),
                                Optional.of(folderPath),
                                userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalseAndContentTypeAndFolderPath(userId,
                                contentType,
                                folderPath, pageable);
        }

        @Test
        void list_EmptyResult_ReturnsEmptyPage() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                Page<File> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

                when(fileRepository.findByUserIdAndDeletedFalse(userId, pageable)).thenReturn(emptyPage);

                // When
                Page<FileResponse> result = fileService.list(pageable, Optional.empty(), Optional.empty(), userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isEqualTo(0);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalse(userId, pageable);
        }

        @Test
        void list_InvalidFolderPath_PathTraversal_ThrowsBadRequestException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                String invalidPath = "/../etc"; // Path traversal attempt

                // When/Then
                assertThatThrownBy(() -> fileService.list(pageable, Optional.empty(), Optional.of(invalidPath), userId))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("path traversal");

                verify(fileRepository, never()).findByUserIdAndDeletedFalse(any(), any());
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

                verify(fileRepository, never()).findByUserIdAndDeletedFalse(any(), any());
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

                verify(fileRepository, never()).findByUserIdAndDeletedFalse(any(), any());
        }

        @Test
        void list_UserNotFound_ThrowsNotFoundException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);
                UUID nonExistentUserId = UUID.randomUUID();

                when(fileRepository.findByUserIdAndDeletedFalse(nonExistentUserId, pageable))
                                .thenReturn(new PageImpl<>(new ArrayList<>(), pageable, 0));

                // When - Note: list doesn't check if user exists, it just queries files
                Page<FileResponse> result = fileService.list(pageable, Optional.empty(), Optional.empty(),
                                nonExistentUserId);

                // Then - Should return empty page, not throw exception
                assertThat(result).isNotNull();
                assertThat(result.getContent()).isEmpty();
        }

        @Test
        void list_NullUserId_ThrowsIllegalArgumentException() {
                // Given
                Pageable pageable = PageRequest.of(0, 20);

                // When/Then
                assertThatThrownBy(() -> fileService.list(pageable, Optional.empty(), Optional.empty(), null))
                                .isInstanceOf(NullPointerException.class); // Repository throws NPE

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalse(null, pageable);
        }

        // update tests

        @Test
        void update_Success_UpdatesFilename() {
                // Given
                FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.update(fileId, request, userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFilename()).isEqualTo("new-filename.txt");

                ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
                verify(fileRepository, times(1)).save(fileCaptor.capture());
                assertThat(fileCaptor.getValue().getFilename()).isEqualTo("new-filename.txt");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
        }

        @Test
        void update_Success_UpdatesFolderPath() {
                // Given
                String newFolderPath = "/photos";
                FileUpdateRequest request = createTestFileUpdateRequest(null, newFolderPath);
                Map<String, Object> moveResult = new HashMap<>();
                moveResult.put("public_id", "new-public-id");
                moveResult.put("url", "http://res.cloudinary.com/test/image/upload/new.jpg");
                moveResult.put("secure_url", "https://res.cloudinary.com/test/image/upload/new.jpg");

                String currentFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());
                String newCloudinaryPath = getExpectedCloudinaryFolderPath(newFolderPath);

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                // moveFile now accepts null resourceType and handles resource type detection
                // internally
                when(storageService.moveFile(eq(currentFullPath), eq(newCloudinaryPath), isNull()))
                                .thenReturn(moveResult);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.update(fileId, request, userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFolderPath()).isEqualTo(newFolderPath);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).moveFile(eq(currentFullPath), eq(newCloudinaryPath), isNull());
                verify(fileRepository, times(1)).save(any(File.class));
        }

        @Test
        void update_Success_UpdatesFolderPath_WithNullUrls_RegeneratesUrls() {
                // Given - Test URL regeneration when moveResult has null URLs
                String newFolderPath = "/photos";
                FileUpdateRequest request = createTestFileUpdateRequest(null, newFolderPath);
                Map<String, Object> moveResult = new HashMap<>();
                moveResult.put("public_id", "new-public-id");
                // URLs are null - should trigger regeneration
                moveResult.put("url", null);
                moveResult.put("secure_url", null);

                String currentFullPath = getExpectedCloudinaryPublicId(testFile.getFolderPath(),
                                testFile.getCloudinaryPublicId());
                String newCloudinaryPath = getExpectedCloudinaryFolderPath(newFolderPath);
                String regeneratedHttpUrl = "http://res.cloudinary.com/test/image/upload/new.jpg";
                String regeneratedHttpsUrl = "https://res.cloudinary.com/test/image/upload/new.jpg";

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(storageService.moveFile(eq(currentFullPath), eq(newCloudinaryPath), isNull()))
                                .thenReturn(moveResult);
                // getFileUrl should be called to regenerate URLs
                when(storageService.getFileUrl(eq("new-public-id"), eq(false))).thenReturn(regeneratedHttpUrl);
                when(storageService.getFileUrl(eq("new-public-id"), eq(true))).thenReturn(regeneratedHttpsUrl);
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                FileResponse response = fileService.update(fileId, request, userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getFolderPath()).isEqualTo(newFolderPath);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).moveFile(eq(currentFullPath), eq(newCloudinaryPath), isNull());
                verify(storageService, times(1)).getFileUrl(eq("new-public-id"), eq(false));
                verify(storageService, times(1)).getFileUrl(eq("new-public-id"), eq(true));
                verify(fileRepository, times(1)).save(any(File.class));
        }

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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).moveFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).moveFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).moveFile(any(), any(), any());
                verify(fileRepository, never()).save(any());
        }

        @Test
        void update_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(fileRepository, never()).save(any());
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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
                verify(fileRepository, never()).save(any());
        }

        @Test
        void update_NullUserId_ThrowsNullPointerException() {
                // Given
                FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);

                // Mock repository to throw NPE when null userId is passed
                when(fileRepository.findByIdAndUserId(fileId, null))
                                .thenThrow(new NullPointerException("User ID cannot be null"));

                // When/Then
                assertThatThrownBy(() -> fileService.update(fileId, request, null))
                                .isInstanceOf(NullPointerException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, null);
        }

        // delete tests

        @Test
        void delete_Success_SoftDeletes() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(testFile));
                when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                fileService.delete(fileId, userId);

                // Then
                ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
                verify(fileRepository, times(1)).save(fileCaptor.capture());
                assertThat(fileCaptor.getValue().getDeleted()).isTrue();
                assertThat(fileCaptor.getValue().getDeletedAt()).isNotNull();

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
        }

        @Test
        void delete_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.delete(fileId, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(fileRepository, never()).save(any());
        }

        @Test
        void delete_BelongsToAnotherUser_ThrowsResourceNotFoundException() {
                // Given
                UUID otherUserId = UUID.randomUUID();
                when(fileRepository.findByIdAndUserId(fileId, otherUserId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.delete(fileId, otherUserId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
                verify(fileRepository, never()).save(any());
        }

        @Test
        void delete_NullUserId_ThrowsNullPointerException() {
                // Given - Mock repository to throw NPE when null userId is passed
                when(fileRepository.findByIdAndUserId(fileId, null))
                                .thenThrow(new NullPointerException("User ID cannot be null"));

                // When/Then
                assertThatThrownBy(() -> fileService.delete(fileId, null))
                                .isInstanceOf(NullPointerException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, null);
        }

        // transform tests

        @Test
        void transform_Success_ReturnsTransformedUrl() {
                // Given - Use image file for transformation
                File imageFile = createTestFile(fileId, userId, "test.jpg");
                imageFile.setContentType("image/jpeg");
                TransformRequest request = createTestTransformRequest(800, 600);
                String originalUrl = "https://res.cloudinary.com/test/image/upload/original.jpg";
                String transformedUrl = "https://res.cloudinary.com/test/image/upload/w_800,h_600/original.jpg";
                String expectedFullPath = getExpectedCloudinaryPublicId(imageFile.getFolderPath(),
                                imageFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(imageFile));
                when(storageService.getFileUrl(eq(expectedFullPath), eq(true))).thenReturn(originalUrl);
                when(storageService.getTransformUrl(eq(expectedFullPath), eq(true), eq(800), eq(600),
                                eq("fill"), eq("auto"), eq("webp"))).thenReturn(transformedUrl);

                // When
                TransformResponse response = fileService.transform(fileId, request, userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getTransformedUrl()).isEqualTo(transformedUrl);
                assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getFileUrl(eq(expectedFullPath), eq(true));
                verify(storageService, times(1)).getTransformUrl(eq(expectedFullPath), eq(true), eq(800),
                                eq(600), eq("fill"), eq("auto"), eq("webp"));
        }

        @Test
        void transform_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                TransformRequest request = createTestTransformRequest(800, 600);

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.transform(fileId, request, userId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).getFileUrl(any(), anyBoolean());
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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, otherUserId);
                verify(storageService, never()).getFileUrl(any(), anyBoolean());
        }

        @Test
        void transform_StorageFailure_ThrowsStorageException() {
                // Given - Use image file for transformation
                File imageFile = createTestFile(fileId, userId, "test.jpg");
                imageFile.setContentType("image/jpeg");
                TransformRequest request = createTestTransformRequest(800, 600);
                String expectedFullPath = getExpectedCloudinaryPublicId(imageFile.getFolderPath(),
                                imageFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(imageFile));
                when(storageService.getFileUrl(eq(expectedFullPath), eq(true)))
                                .thenThrow(new StorageException("Storage error"));

                // When/Then
                assertThatThrownBy(() -> fileService.transform(fileId, request, userId))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("Storage error");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getFileUrl(eq(expectedFullPath), eq(true));
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

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).getFileUrl(any(), anyBoolean());
        }

        // getTransformUrl tests

        @Test
        void getTransformUrl_Success_ReturnsUrl() {
                // Given - Use image file for transformation
                File imageFile = createTestFile(fileId, userId, "test.jpg");
                imageFile.setContentType("image/jpeg");
                String originalUrl = "https://res.cloudinary.com/test/image/upload/original.jpg";
                String transformedUrl = "https://res.cloudinary.com/test/image/upload/w_800,h_600/original.jpg";
                String expectedFullPath = getExpectedCloudinaryPublicId(imageFile.getFolderPath(),
                                imageFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(imageFile));
                when(storageService.getFileUrl(eq(expectedFullPath), eq(true))).thenReturn(originalUrl);
                when(storageService.getTransformUrl(eq(expectedFullPath), eq(true), eq(800), eq(600),
                                isNull(), isNull(), isNull())).thenReturn(transformedUrl);

                // When
                TransformResponse response = fileService.getTransformUrl(fileId, 800, 600, Optional.empty(),
                                Optional.empty(), Optional.empty(), userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getTransformedUrl()).isEqualTo(transformedUrl);
                assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getFileUrl(eq(expectedFullPath), eq(true));
                verify(storageService, times(1)).getTransformUrl(eq(expectedFullPath), eq(true), eq(800),
                                eq(600), isNull(), isNull(), isNull());
        }

        @Test
        void getTransformUrl_FileNotFound_ThrowsResourceNotFoundException() {
                // Given
                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> fileService.getTransformUrl(fileId, 800, 600, Optional.empty(),
                                Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(CloudFileNotFoundException.class);

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, never()).getFileUrl(any(), anyBoolean());
        }

        @Test
        void getTransformUrl_StorageFailure_ThrowsStorageException() {
                // Given - Use image file for transformation
                File imageFile = createTestFile(fileId, userId, "test.jpg");
                imageFile.setContentType("image/jpeg");
                String expectedFullPath = getExpectedCloudinaryPublicId(imageFile.getFolderPath(),
                                imageFile.getCloudinaryPublicId());

                when(fileRepository.findByIdAndUserId(fileId, userId)).thenReturn(Optional.of(imageFile));
                when(storageService.getFileUrl(eq(expectedFullPath), eq(true)))
                                .thenThrow(new StorageException("Storage error"));

                // When/Then
                assertThatThrownBy(() -> fileService.getTransformUrl(fileId, 800, 600, Optional.empty(),
                                Optional.empty(), Optional.empty(), userId))
                                .isInstanceOf(StorageException.class)
                                .hasMessageContaining("Storage error");

                verify(fileRepository, times(1)).findByIdAndUserId(fileId, userId);
                verify(storageService, times(1)).getFileUrl(eq(expectedFullPath), eq(true));
        }

        // search tests

        @Test
        void search_Success_ReturnsResults() {
                // Given
                String query = "test";
                Pageable pageable = PageRequest.of(0, 20);
                List<File> files = Arrays.asList(testFile);
                Page<File> filePage = new PageImpl<>(files, pageable, 1);

                when(fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(userId, query, pageable))
                                .thenReturn(filePage);

                // When
                Page<FileResponse> result = fileService.search(query, Optional.empty(), Optional.empty(), pageable,
                                userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getTotalElements()).isEqualTo(1);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(userId,
                                query,
                                pageable);
        }

        @Test
        void search_WithFilters_ReturnsFilteredResults() {
                // Given
                String query = "test";
                String contentType = "text/plain";
                Pageable pageable = PageRequest.of(0, 20);
                List<File> files = Arrays.asList(testFile);
                Page<File> filePage = new PageImpl<>(files, pageable, 1);

                when(fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCaseWithFilters(userId, query,
                                contentType, folderPath, pageable)).thenReturn(filePage);

                // When
                Page<FileResponse> result = fileService.search(query, Optional.of(contentType), Optional.of(folderPath),
                                pageable, userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCaseWithFilters(
                                userId,
                                query, contentType, folderPath, pageable);
        }

        @Test
        void search_NoResults_ReturnsEmptyPage() {
                // Given
                String query = "nonexistent";
                Pageable pageable = PageRequest.of(0, 20);
                Page<File> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

                when(fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(userId, query, pageable))
                                .thenReturn(emptyPage);

                // When
                Page<FileResponse> result = fileService.search(query, Optional.empty(), Optional.empty(), pageable,
                                userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isEqualTo(0);

                verify(fileRepository, times(1)).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(userId,
                                query,
                                pageable);
        }

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

                verify(fileRepository, never()).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(any(),
                                anyString(), any());
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

                verify(fileRepository, never()).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(any(),
                                anyString(), any());
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

                verify(fileRepository, never()).findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(any(),
                                anyString(), any());
        }

        @Test
        void search_UserNotFound_ThrowsNotFoundException() {
                // Given
                String query = "test";
                Pageable pageable = PageRequest.of(0, 20);
                UUID nonExistentUserId = UUID.randomUUID();

                when(fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(nonExistentUserId, query,
                                pageable)).thenReturn(new PageImpl<>(new ArrayList<>(), pageable, 0));

                // When - Note: search doesn't check if user exists, it just queries files
                Page<FileResponse> result = fileService.search(query, Optional.empty(), Optional.empty(), pageable,
                                nonExistentUserId);

                // Then - Should return empty page, not throw exception
                assertThat(result).isNotNull();
                assertThat(result.getContent()).isEmpty();
        }

        // getStatistics tests

        @Test
        void getStatistics_Success_ReturnsStatistics() {
                // Given
                Map<String, Object> baseStats = new HashMap<>();
                baseStats.put("total_files", 10L);
                baseStats.put("total_size", 1048576L);
                baseStats.put("average_file_size", 104857L);

                List<Object[]> contentTypeCounts = Arrays.asList(
                                new Object[] { "text/plain", 5L },
                                new Object[] { "image/jpeg", 3L },
                                new Object[] { "application/pdf", 2L });

                List<Object[]> folderCounts = Arrays.asList(
                                new Object[] { "", 3L },
                                new Object[] { "/documents", 7L });

                when(fileRepository.getFileStatisticsByUserId(userId)).thenReturn(baseStats);
                when(fileRepository.getContentTypeCountsByUserId(userId)).thenReturn(contentTypeCounts);
                when(fileRepository.getFolderCountsByUserId(userId)).thenReturn(folderCounts);

                // When
                FileStatisticsResponse response = fileService.getStatistics(userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getTotalFiles()).isEqualTo(10L);
                assertThat(response.getTotalSize()).isEqualTo(1048576L);
                assertThat(response.getAverageFileSize()).isEqualTo(104857L);
                assertThat(response.getStorageUsed()).isNotNull();
                assertThat(response.getByContentType()).isNotEmpty();
                assertThat(response.getByContentType().get("text/plain")).isEqualTo(5L);
                assertThat(response.getByFolder()).isNotEmpty();

                verify(fileRepository, times(1)).getFileStatisticsByUserId(userId);
                verify(fileRepository, times(1)).getContentTypeCountsByUserId(userId);
                verify(fileRepository, times(1)).getFolderCountsByUserId(userId);
        }

        @Test
        void getStatistics_NoFiles_ReturnsZeroStatistics() {
                // Given
                Map<String, Object> baseStats = new HashMap<>();
                baseStats.put("total_files", 0L);
                baseStats.put("total_size", 0L);
                baseStats.put("average_file_size", 0L);

                when(fileRepository.getFileStatisticsByUserId(userId)).thenReturn(baseStats);
                when(fileRepository.getContentTypeCountsByUserId(userId)).thenReturn(new ArrayList<>());
                when(fileRepository.getFolderCountsByUserId(userId)).thenReturn(new ArrayList<>());

                // When
                FileStatisticsResponse response = fileService.getStatistics(userId);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getTotalFiles()).isEqualTo(0L);
                assertThat(response.getTotalSize()).isEqualTo(0L);
                assertThat(response.getAverageFileSize()).isEqualTo(0L);
                assertThat(response.getByContentType()).isEmpty();
                assertThat(response.getByFolder()).isEmpty();

                verify(fileRepository, times(1)).getFileStatisticsByUserId(userId);
        }

        @Test
        void getStatistics_UserNotFound_ThrowsNotFoundException() {
                // Given
                UUID nonExistentUserId = UUID.randomUUID();
                Map<String, Object> baseStats = new HashMap<>();
                baseStats.put("total_files", 0L);
                baseStats.put("total_size", 0L);
                baseStats.put("average_file_size", 0L);

                when(fileRepository.getFileStatisticsByUserId(nonExistentUserId)).thenReturn(baseStats);
                when(fileRepository.getContentTypeCountsByUserId(nonExistentUserId)).thenReturn(new ArrayList<>());
                when(fileRepository.getFolderCountsByUserId(nonExistentUserId)).thenReturn(new ArrayList<>());

                // When - Note: getStatistics doesn't check if user exists, it just queries
                // files
                FileStatisticsResponse response = fileService.getStatistics(nonExistentUserId);

                // Then - Should return zero statistics, not throw exception
                assertThat(response).isNotNull();
                assertThat(response.getTotalFiles()).isEqualTo(0L);
        }

        // Helper methods
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

        private Map<String, Object> createTestCloudinaryResponse(String publicId) {
                Map<String, Object> response = new HashMap<>();
                response.put("public_id", publicId);
                response.put("url", "http://res.cloudinary.com/test/image/upload/" + publicId + ".jpg");
                response.put("secure_url", "https://res.cloudinary.com/test/image/upload/" + publicId + ".jpg");
                response.put("format", "jpg");
                response.put("resource_type", "image");
                return response;
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

        /**
         * Helper method to construct expected Cloudinary folder path with userId
         * prefix.
         * Format: {userId}/{userFolderPath} or {userId} for root.
         */
        private String getExpectedCloudinaryFolderPath(String folderPath) {
                if (folderPath == null || folderPath.isEmpty()) {
                        return userId.toString();
                }
                String normalizedFolder = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
                return userId + "/" + normalizedFolder;
        }

        /**
         * Helper method to construct expected Cloudinary public ID with userId prefix.
         * Format: {userId}/{userFolderPath}/{uuid} or {userId}/{uuid} for root.
         */
        private String getExpectedCloudinaryPublicId(String folderPath, String publicId) {
                if (folderPath == null || folderPath.isEmpty()) {
                        return userId + "/" + publicId;
                }
                String normalizedFolder = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
                return userId + "/" + normalizedFolder + "/" + publicId;
        }
}
