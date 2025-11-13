package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationRequest;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.NotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FolderServiceImpl focusing on error contracts and edge cases.
 * Success scenarios are covered by integration tests.
 * These tests verify what exceptions are thrown, not how the code works
 * internally.
 */
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

    // ==================== ValidateFolderPath Error Contract Tests
    // ====================

    @Test
    void validateFolderPath_InvalidPathFormat_ReturnsInvalidResult() {
        // Given - Path with invalid characters that will fail validation even after
        // normalization
        String invalidPath = "/documents/../etc"; // Path traversal - should fail validation
        FolderPathValidationRequest request = createTestValidationRequest(invalidPath);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        var result = folderService.validateFolderPath(request, userId);

        // Then - Should return invalid result, not throw
        assertThatThrownBy(() -> {
            if (!result.isValid()) {
                throw new BadRequestException(result.getMessage());
            }
        }).isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateFolderPath_PathWithTraversalSequence_ReturnsInvalidResult() {
        // Given
        String invalidPath = "/documents/../etc";
        FolderPathValidationRequest request = createTestValidationRequest(invalidPath);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        var result = folderService.validateFolderPath(request, userId);

        // Then
        assertThatThrownBy(() -> {
            if (!result.isValid()) {
                throw new BadRequestException(result.getMessage());
            }
        }).isInstanceOf(BadRequestException.class);
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
    }

    @Test
    void validateFolderPath_NullUserId_ThrowsIllegalArgumentException() {
        // Given
        FolderPathValidationRequest request = createTestValidationRequest(folderPath);

        // When/Then
        assertThatThrownBy(() -> folderService.validateFolderPath(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // ==================== ListFolders Error Contract Tests ====================

    @Test
    void listFolders_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.listFolders(Optional.empty(), userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void listFolders_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.listFolders(Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // ==================== DeleteFolder Error Contract Tests ====================

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
    }

    @Test
    void deleteFolder_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void deleteFolder_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.deleteFolder(folderPath, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // ==================== GetFolderStatistics Error Contract Tests
    // ====================

    @Test
    void getFolderStatistics_UserNotFound_ThrowsNotFoundException() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> folderService.getFolderStatistics(folderPath, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getFolderStatistics_NullUserId_ThrowsIllegalArgumentException() {
        // When/Then
        assertThatThrownBy(() -> folderService.getFolderStatistics(folderPath, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
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

    private FolderPathValidationRequest createTestValidationRequest(String path) {
        FolderPathValidationRequest request = new FolderPathValidationRequest();
        request.setPath(path);
        request.setDescription("Test folder");
        return request;
    }
}
