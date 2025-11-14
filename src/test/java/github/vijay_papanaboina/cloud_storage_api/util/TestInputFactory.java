package github.vijay_papanaboina.cloud_storage_api.util;

import java.util.Arrays;
import java.util.List;

/**
 * Centralized factory for test input data.
 * Provides common test inputs for security testing, validation testing, etc.
 */
public class TestInputFactory {

    private TestInputFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Path traversal examples for testing security validation.
     */
    public static List<String> pathTraversalExamples() {
        return Arrays.asList(
                "../etc/passwd",
                "/documents/../etc",
                "..",
                "../../../etc/passwd",
                "/documents/../../etc",
                "documents/../etc",
                "/documents/..",
                "/documents/./../etc"
        );
    }

    /**
     * SQL injection patterns for testing LIKE wildcard escaping.
     */
    public static List<String> sqlInjectionPatterns() {
        return Arrays.asList(
                "test%file",
                "test_file",
                "test\\file",
                "test%_file",
                "test\\%file",
                "test%\\_file",
                "%test%",
                "_test_",
                "test\\%\\_file"
        );
    }

    /**
     * Reserved Windows filenames for testing filename sanitization.
     */
    public static List<String> reservedFilenames() {
        return Arrays.asList(
                "CON",
                "PRN",
                "AUX",
                "NUL",
                "COM1",
                "COM2",
                "COM3",
                "COM4",
                "COM5",
                "COM6",
                "COM7",
                "COM8",
                "COM9",
                "LPT1",
                "LPT2",
                "LPT3",
                "LPT4",
                "LPT5",
                "LPT6",
                "LPT7",
                "LPT8",
                "LPT9"
        );
    }

    /**
     * Malicious filenames for testing sanitization.
     */
    public static List<String> maliciousFilenames() {
        return Arrays.asList(
                "folder/test.txt",
                "test\0.txt",
                "test\t\n\r.txt",
                "  test.txt  ",
                "test..txt",
                ".test",
                "..test"
        );
    }

    /**
     * Invalid folder paths for testing validation.
     */
    public static List<String> invalidFolderPaths() {
        return Arrays.asList(
                null,
                "",
                "documents",  // No leading slash
                "../etc",
                "/documents//photos",  // Consecutive slashes
                "/documents/../etc",  // Path traversal
                "/documents/..",  // Path traversal
                "/" + "a".repeat(500)  // Exceeds max length
        );
    }

    /**
     * Invalid filenames for testing validation.
     */
    public static List<String> invalidFilenames() {
        return Arrays.asList(
                null,
                "",
                "folder/test.txt",  // Path separator
                "test\0.txt",  // Null byte
                "test\t\n\r.txt",  // Control characters
                "   ",  // Whitespace only
                "a".repeat(256),  // Exceeds max length
                "CON.txt",  // Reserved name
                "PRN",  // Reserved name
                ".",  // Special name
                ".."  // Special name
        );
    }
}

