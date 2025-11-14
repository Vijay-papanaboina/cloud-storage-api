package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_ValidPath_ReturnsNormalized() {
        String result = folderService.normalizeFolderPath("/documents");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_WithTrailingSlash_RemovesTrailingSlash() {
        String result = folderService.normalizeFolderPath("/documents/");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_RootPath_KeepsRoot() {
        String result = folderService.normalizeFolderPath("/");
        assertThat(result).isEqualTo("/");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_WithoutLeadingSlash_AddsLeadingSlash() {
        String result = folderService.normalizeFolderPath("documents");
        assertThat(result).isEqualTo("/documents");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_Null_ReturnsNull() {
        String result = folderService.normalizeFolderPath(null);
        assertThat(result).isNull();
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_Empty_ReturnsNull() {
        String result = folderService.normalizeFolderPath("");
        assertThat(result).isNull();
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void normalizeFolderPath_OnlyWhitespace_ReturnsRoot() {
        // After trimming, empty string gets leading slash added, resulting in "/"
        String result = folderService.normalizeFolderPath("   ");
        assertThat(result).isEqualTo("/");
    }

    // ==================== validateFolderPath Tests ====================

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void validateFolderPath_Null_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Folder path is required");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void validateFolderPath_Empty_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Folder path is required");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void validateFolderPath_WithoutLeadingSlash_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("documents"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must start with '/'");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void validateFolderPath_ExceedsMaxLength_ThrowsBadRequestException() {
        String longPath = "/" + "a".repeat(500);
        assertThatThrownBy(() -> folderService.validateFolderPath(longPath))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not exceed 500 characters");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    void validateFolderPath_WithConsecutiveSlashes_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents//photos"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consecutive slashes");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    @Tag("security")
    void validateFolderPath_WithPathTraversal_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents/../etc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent directory references");
    }

    @Test
    @Tag("pure-logic")
    @Tag("path-validation")
    @Tag("security")
    void validateFolderPath_WithDoubleDot_ThrowsBadRequestException() {
        assertThatThrownBy(() -> folderService.validateFolderPath("/documents/.."))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent directory references");
    }

    // ==================== Property-Based Security Tests ====================

    @BeforeTry
    void ensureFolderServiceInitialized() {
        if (folderService == null) {
            folderService = new FolderServiceImpl(null, null);
        }
    }

    @Property(tries = 100)
    // Note: @Property methods cannot have JUnit @Tag annotations
    boolean validateFolderPath_RejectsAllPathTraversalAttempts(@ForAll @StringLength(min = 1, max = 200) String input) {
        // Generate random strings that might contain path traversal patterns
        // If input contains ".." or path traversal, it should be rejected
        try {
            folderService.validateFolderPath(input);
            // If validation succeeds, verify no path traversal patterns
            return !input.contains("..");
        } catch (BadRequestException e) {
            // Exception is acceptable for invalid paths (path traversal, etc.)
            // Verify that path traversal attempts are caught
            if (input.contains("..")) {
                return e.getMessage().contains("parent directory references") ||
                        e.getMessage().contains("must start with") ||
                        e.getMessage().contains("consecutive slashes");
            }
            // Other validation errors are also acceptable
            return true;
        }
    }
}
