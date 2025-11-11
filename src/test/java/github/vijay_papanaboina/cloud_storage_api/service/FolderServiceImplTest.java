package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationResult;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderStatisticsResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.NotFoundException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FolderServiceImpl folderService;

    private UUID userId;
    private User testUser;
    private String folderPath;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = createTestUser(userId);
        folderPath = "/documents";
    }

    // validateFolderPath tests

    @Test
    void validateFolderPath_Success_PathValidAndAvailable() {
        // Given
        FolderPathValidationRequest request = createTestValidationRequest(folderPath);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(0L);

        // When
        FolderPathValidationResult result = folderService.validateFolderPath(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.isExists()).isFalse();
        assertThat(result.getPath()).isEqualTo(folderPath);
        assertThat(result.getFileCount()).isEqualTo(0L);
        assertThat(result.getMessage()).contains("valid and available");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
    }

    @Test
    void validateFolderPath_Success_PathValidButExists() {
        // Given
        FolderPathValidationRequest request = createTestValidationRequest(folderPath);
        long fileCount = 5L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(fileCount);

        // When
        FolderPathValidationResult result = folderService.validateFolderPath(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.isExists()).isTrue();
        assertThat(result.getPath()).isEqualTo(folderPath);
        assertThat(result.getFileCount()).isEqualTo(fileCount);
        assertThat(result.getMessage()).contains("already exists");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
    }

    @Test
    void validateFolderPath_InvalidPathFormat_ReturnsInvalidResult() {
        // Given
        String invalidPath = "invalid-path"; // Doesn't start with "/"
        FolderPathValidationRequest request = createTestValidationRequest(invalidPath);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        FolderPathValidationResult result = folderService.validateFolderPath(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.isExists()).isFalse();
        assertThat(result.getMessage()).isNotNull();
        assertThat(result.getFileCount()).isNull();

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    @Test
    void validateFolderPath_PathWithTraversalSequence_ReturnsInvalidResult() {
        // Given
        String invalidPath = "/documents/../etc";
        FolderPathValidationRequest request = createTestValidationRequest(invalidPath);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        FolderPathValidationResult result = folderService.validateFolderPath(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.isExists()).isFalse();
        assertThat(result.getMessage()).contains("parent directory references");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    @Test
    void validateFolderPath_UserNotFound_ThrowsNotFoundException() {
        // Given
        FolderPathValidationRequest request = createTestValidationRequest(folderPath);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.validateFolderPath(request, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    @Test
    void validateFolderPath_NullUserId_ThrowsIllegalArgumentException() {
        // Given
        FolderPathValidationRequest request = createTestValidationRequest(folderPath);

        // When/Then
        assertThatThrownBy(() -> folderService.validateFolderPath(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    // listFolders tests

    @Test
    void listFolders_Success_AllFolders() {
        // Given
        List<String> folderPaths = Arrays.asList("/documents", "/photos", "/videos");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId)).thenReturn(folderPaths);
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, "/documents")).thenReturn(5L);
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, "/photos")).thenReturn(3L);
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, "/videos")).thenReturn(2L);
        when(fileRepository.getFolderStatisticsByUserIdAndFolderPath(eq(userId), anyString()))
                .thenReturn(createTestFolderStats(Instant.now()));

        // When
        List<FolderResponse> result = folderService.listFolders(Optional.empty(), userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPath()).isEqualTo("/documents");
        assertThat(result.get(0).getFileCount()).isEqualTo(5);
        assertThat(result.get(1).getPath()).isEqualTo("/photos");
        assertThat(result.get(1).getFileCount()).isEqualTo(3);

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).findDistinctFolderPathsByUserIdAndDeletedFalse(userId);
    }

    @Test
    void listFolders_WithParentPath_ReturnsDirectChildren() {
        // Given
        String parentPath = "/documents";
        List<String> allPaths = Arrays.asList("/documents", "/documents/2024", "/documents/2024/january", "/photos");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId)).thenReturn(allPaths);
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, "/documents/2024")).thenReturn(10L);
        when(fileRepository.getFolderStatisticsByUserIdAndFolderPath(eq(userId), anyString()))
                .thenReturn(createTestFolderStats(Instant.now()));

        // When
        List<FolderResponse> result = folderService.listFolders(Optional.of(parentPath), userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("/documents/2024");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).findDistinctFolderPathsByUserIdAndDeletedFalse(userId);
    }

    @Test
    void listFolders_NoFolders_ReturnsEmptyList() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId)).thenReturn(new ArrayList<>());

        // When
        List<FolderResponse> result = folderService.listFolders(Optional.empty(), userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).findDistinctFolderPathsByUserIdAndDeletedFalse(userId);
    }

    @Test
    void listFolders_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.listFolders(Optional.empty(), userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).findDistinctFolderPathsByUserIdAndDeletedFalse(any());
    }

    @Test
    void listFolders_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.listFolders(Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
        verify(fileRepository, never()).findDistinctFolderPathsByUserIdAndDeletedFalse(any());
    }

    // deleteFolder tests

    @Test
    void deleteFolder_FolderNotEmpty_ThrowsBadRequestException() {
        // Given
        long fileCount = 5L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(fileCount);

        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete non-empty folder");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
    }

    @Test
    void deleteFolder_FolderNotFound_ThrowsResourceNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(0L);

        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Folder not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
    }

    @Test
    void deleteFolder_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    @Test
    void deleteFolder_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    // getFolderStatistics tests

    @Test
    void getFolderStatistics_Success_WithFiles() {
        // Given
        long fileCount = 10L;
        long totalSize = 1048576L; // 1 MB
        Map<String, Object> stats = createTestFolderStats(Instant.now());
        stats.put("file_count", fileCount);
        stats.put("total_size", totalSize);

        List<Object[]> contentTypeCounts = Arrays.asList(
                new Object[]{"text/plain", 5L},
                new Object[]{"image/jpeg", 3L},
                new Object[]{"application/pdf", 2L}
        );
        List<String> allFolders = Arrays.asList(folderPath, folderPath + "/subfolder");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(fileCount);
        when(fileRepository.getFolderStatisticsByUserIdAndFolderPath(userId, folderPath)).thenReturn(stats);
        when(fileRepository.getFolderContentTypeCountsByUserIdAndFolderPath(userId, folderPath))
                .thenReturn(contentTypeCounts);
        when(fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId)).thenReturn(allFolders);
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath + "/subfolder"))
                .thenReturn(3L);

        // When
        FolderStatisticsResponse result = folderService.getFolderStatistics(folderPath, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPath()).isEqualTo(folderPath);
        assertThat(result.getTotalFiles()).isEqualTo(fileCount);
        assertThat(result.getTotalSize()).isEqualTo(totalSize);
        assertThat(result.getAverageFileSize()).isEqualTo(totalSize / fileCount);
        assertThat(result.getByContentType()).isNotEmpty();
        assertThat(result.getByContentType().get("text/plain")).isEqualTo(5L);

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
        verify(fileRepository, times(1)).getFolderStatisticsByUserIdAndFolderPath(userId, folderPath);
    }

    @Test
    void getFolderStatistics_EmptyFolder_ReturnsZeroStatistics() {
        // Given
        long fileCount = 0L;
        Map<String, Object> stats = new HashMap<>();
        stats.put("file_count", 0L);
        stats.put("total_size", 0L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath)).thenReturn(fileCount);
        when(fileRepository.getFolderStatisticsByUserIdAndFolderPath(userId, folderPath)).thenReturn(stats);
        when(fileRepository.getFolderContentTypeCountsByUserIdAndFolderPath(userId, folderPath))
                .thenReturn(new ArrayList<>());
        when(fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId)).thenReturn(new ArrayList<>());

        // When/Then
        assertThatThrownBy(() -> folderService.getFolderStatistics(folderPath, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Folder not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, times(1)).countByUserIdAndFolderPathAndDeletedFalse(userId, folderPath);
    }

    @Test
    void getFolderStatistics_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.getFolderStatistics(folderPath, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).findById(userId);
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
    }

    @Test
    void getFolderStatistics_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.getFolderStatistics(folderPath, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
        verify(fileRepository, never()).countByUserIdAndFolderPathAndDeletedFalse(any(), any());
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

    private File createTestFile(UUID fileId, UUID userId, String folderPath) {
        File file = new File();
        file.setId(fileId);
        file.setUser(testUser);
        file.setFilename("test.txt");
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

    private FolderPathValidationRequest createTestValidationRequest(String path) {
        FolderPathValidationRequest request = new FolderPathValidationRequest();
        request.setPath(path);
        request.setDescription("Test folder");
        return request;
    }

    private Map<String, Object> createTestFolderStats(Instant createdAt) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("file_count", 10L);
        stats.put("total_size", 1048576L);
        stats.put("created_at", createdAt);
        return stats;
    }
}

