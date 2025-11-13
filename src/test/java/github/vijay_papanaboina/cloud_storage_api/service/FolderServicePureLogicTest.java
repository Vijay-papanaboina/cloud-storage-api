package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for pure logic methods in FolderServiceImpl.
 * These tests focus on behavior without mocking, testing pure functions in
 * isolation.
 */
class FolderServicePureLogicTest {

    private FolderServiceImpl folderService;

    @BeforeEach
    void setUp() {
        // Create instance to test package-private methods
        // These are pure logic methods that don't use dependencies
        folderService = new FolderServiceImpl(null, null);
    }

    // ==================== normalizeFolderPath Tests ====================

    @Test
    void normalizeFolderPath_ValidPath_ReturnsNormalized() {
        String result = folderService.normalizeFolderPath("/documents");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    void normalizeFolderPath_WithTrailingSlash_RemovesTrailingSlash() {
        String result = folderService.normalizeFolderPath("/documents/");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    void normalizeFolderPath_RootPath_KeepsRoot() {
        String result = folderService.normalizeFolderPath("/");
        assertThat(result).isEqualTo("/");
    }

    @Test
    void normalizeFolderPath_WithoutLeadingSlash_AddsLeadingSlash() {
        String result = folderService.normalizeFolderPath("documents");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    void normalizeFolderPath_Null_ReturnsNull() {
        String result = folderService.normalizeFolderPath(null);
        assertThat(result).isNull();
    }

    @Test
    void normalizeFolderPath_Empty_ReturnsNull() {
        String result = folderService.normalizeFolderPath("");
        assertThat(result).isNull();
    }

    @Test
    void normalizeFolderPath_OnlyWhitespace_ReturnsRoot() {
        // After trimming, empty string gets leading slash added, resulting in "/"
        String result = folderService.normalizeFolderPath("   ");
        assertThat(result).isEqualTo("/");
    }

    // ==================== validateFolderPath Tests ====================

    @Test
    void validateFolderPath_Null_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Folder path is required");
    }

    @Test
    void validateFolderPath_Empty_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Folder path is required");
    }

    @Test
    void validateFolderPath_WithoutLeadingSlash_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("documents"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must start with '/'");
    }

    @Test
    void validateFolderPath_ExceedsMaxLength_ThrowsBadRequestException() {
        String longPath = "/" + "a".repeat(500);
        assertThatThrownBy(() -> folderService.validateFolderPath(longPath))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not exceed 500 characters");
    }

    @Test
    void validateFolderPath_WithConsecutiveSlashes_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents//photos"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consecutive slashes");
    }

    @Test
    void validateFolderPath_WithPathTraversal_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents/../etc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent directory references");
    }

    @Test
    void validateFolderPath_WithDoubleDot_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents/.."))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent directory references");
    }
}
