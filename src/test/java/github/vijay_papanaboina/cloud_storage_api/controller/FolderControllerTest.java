package github.vijay_papanaboina.cloud_storage_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.FolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FolderController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FolderService folderService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID userId;
    private String folderPath;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        folderPath = "/photos/2024";
    }

    // POST /api/folders tests

    @Test
    @WithMockUser
    void createFolder_Success_Returns201() throws Exception {
        // Given
        FolderCreateRequest request = new FolderCreateRequest(folderPath, "Test folder description");
        FolderPathValidationResult validationResult = new FolderPathValidationResult(
                folderPath, true, false, "Path is valid", 0L);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.validateFolderPath(any(FolderPathValidationRequest.class), eq(userId)))
                    .thenReturn(validationResult);

            // When/Then
            mockMvc.perform(post("/api/folders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(folderPath))
                    .andExpect(jsonPath("$.description").value("Test folder description"))
                    .andExpect(jsonPath("$.fileCount").value(0))
                    .andExpect(jsonPath("$.createdAt").exists());

            verify(folderService, times(1)).validateFolderPath(any(FolderPathValidationRequest.class), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void createFolder_MissingPath_Returns400() throws Exception {
        // Given
        FolderCreateRequest request = new FolderCreateRequest();
        request.setDescription("Test description");

        // When/Then
        mockMvc.perform(post("/api/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(folderService, never()).validateFolderPath(any(), any());
    }

    @Test
    @WithMockUser
    void createFolder_InvalidPath_Returns400() throws Exception {
        // Given - Path doesn't start with '/' - will fail DTO validation
        // (@SafeFolderPath)
        FolderCreateRequest request = new FolderCreateRequest("invalid-path", "Test description");

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then - DTO validation fails before controller method is called
            mockMvc.perform(post("/api/folders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Service method should not be called because DTO validation fails first
            verify(folderService, never()).validateFolderPath(any(), any());
        }
    }

    @Test
    @WithMockUser
    void createFolder_PathAlreadyExists_Returns409() throws Exception {
        // Given
        FolderCreateRequest request = new FolderCreateRequest(folderPath, "Test description");
        FolderPathValidationResult validationResult = new FolderPathValidationResult(
                folderPath, true, true, "Path already exists", 5L);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.validateFolderPath(any(FolderPathValidationRequest.class), eq(userId)))
                    .thenReturn(validationResult);

            // When/Then
            mockMvc.perform(post("/api/folders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(folderService, times(1)).validateFolderPath(any(FolderPathValidationRequest.class), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void createFolder_PathTraversal_Returns400() throws Exception {
        // Given - Path that will fail validation (contains invalid characters or
        // escapes root)
        // Using a path that, after normalization attempts, would escape root: /../etc
        FolderCreateRequest request = new FolderCreateRequest("/../etc", "Test description");

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then - SafeFolderPath validator should reject this
            mockMvc.perform(post("/api/folders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(folderService, never()).validateFolderPath(any(), any());
        }
    }

    @Test
    void createFolder_Unauthenticated_Returns403() throws Exception {
        // Given
        FolderCreateRequest request = new FolderCreateRequest(folderPath, "Test description");

        // When/Then
        mockMvc.perform(post("/api/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(folderService, never()).validateFolderPath(any(), any());
    }
    // GET /api/folders tests

    @Test
    @WithMockUser
    void listFolders_Success_Returns200() throws Exception {
        // Given
        List<FolderResponse> folders = new ArrayList<>();
        folders.add(createTestFolderResponse("/photos/2024", 10));
        folders.add(createTestFolderResponse("/documents", 5));

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.listFolders(eq(Optional.empty()), eq(userId))).thenReturn(folders);

            // When/Then
            mockMvc.perform(get("/api/folders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].path").value("/photos/2024"))
                    .andExpect(jsonPath("$[0].fileCount").value(10))
                    .andExpect(jsonPath("$[1].path").value("/documents"))
                    .andExpect(jsonPath("$[1].fileCount").value(5));

            verify(folderService, times(1)).listFolders(eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void listFolders_WithParentPath_Returns200() throws Exception {
        // Given
        String parentPath = "/photos";
        List<FolderResponse> folders = new ArrayList<>();
        folders.add(createTestFolderResponse("/photos/2024", 10));

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.listFolders(eq(Optional.of(parentPath)), eq(userId))).thenReturn(folders);

            // When/Then
            mockMvc.perform(get("/api/folders")
                    .param("parentPath", parentPath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].path").value("/photos/2024"));

            verify(folderService, times(1)).listFolders(eq(Optional.of(parentPath)), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void listFolders_Empty_Returns200() throws Exception {
        // Given
        List<FolderResponse> folders = new ArrayList<>();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.listFolders(eq(Optional.empty()), eq(userId))).thenReturn(folders);

            // When/Then
            mockMvc.perform(get("/api/folders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(folderService, times(1)).listFolders(eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    void listFolders_Unauthenticated_Returns403() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isForbidden());

        verify(folderService, never()).listFolders(any(), any());
    }
    // GET /api/folders/statistics tests

    @Test
    @WithMockUser
    void getFolderStatistics_Success_Returns200() throws Exception {
        // Given
        FolderStatisticsResponse statistics = createTestFolderStatisticsResponse(folderPath);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.getFolderStatistics(eq(folderPath), eq(userId))).thenReturn(statistics);

            // When/Then
            mockMvc.perform(get("/api/folders/statistics")
                    .param("path", folderPath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(folderPath))
                    .andExpect(jsonPath("$.totalFiles").value(10))
                    .andExpect(jsonPath("$.totalSize").value(1048576))
                    .andExpect(jsonPath("$.averageFileSize").value(104857))
                    .andExpect(jsonPath("$.storageUsed").value("1.00 MB"))
                    .andExpect(jsonPath("$.byContentType").exists())
                    .andExpect(jsonPath("$.byFolder").exists());

            verify(folderService, times(1)).getFolderStatistics(eq(folderPath), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void getFolderStatistics_MissingPath_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/folders/statistics"))
                    .andExpect(status().isBadRequest());

            verify(folderService, never()).getFolderStatistics(any(), any());
        }
    }

    @Test
    @WithMockUser
    void getFolderStatistics_EmptyPath_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/folders/statistics")
                    .param("path", ""))
                    .andExpect(status().isBadRequest());

            verify(folderService, never()).getFolderStatistics(any(), any());
        }
    }

    @Test
    @WithMockUser
    void getFolderStatistics_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(folderService.getFolderStatistics(eq(folderPath), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("Folder not found: " + folderPath));

            // When/Then
            mockMvc.perform(get("/api/folders/statistics")
                    .param("path", folderPath))
                    .andExpect(status().isNotFound());

            verify(folderService, times(1)).getFolderStatistics(eq(folderPath), eq(userId));
        }
    }

    @Test
    void getFolderStatistics_Unauthenticated_Returns403() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/folders/statistics")
                .param("path", folderPath))
                .andExpect(status().isForbidden());

        verify(folderService, never()).getFolderStatistics(any(), any());
    }
    // DELETE /api/folders tests

    @Test
    @WithMockUser
    void deleteFolder_Success_Returns204() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doNothing().when(folderService).deleteFolder(eq(folderPath), eq(userId));

            // When/Then
            mockMvc.perform(delete("/api/folders")
                    .param("path", folderPath))
                    .andExpect(status().isNoContent());

            verify(folderService, times(1)).deleteFolder(eq(folderPath), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void deleteFolder_NotEmpty_Returns400() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doThrow(new BadRequestException("Folder is not empty"))
                    .when(folderService).deleteFolder(eq(folderPath), eq(userId));

            // When/Then
            mockMvc.perform(delete("/api/folders")
                    .param("path", folderPath))
                    .andExpect(status().isBadRequest());

            verify(folderService, times(1)).deleteFolder(eq(folderPath), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void deleteFolder_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doThrow(new ResourceNotFoundException("Folder not found: " + folderPath))
                    .when(folderService).deleteFolder(eq(folderPath), eq(userId));

            // When/Then
            mockMvc.perform(delete("/api/folders")
                    .param("path", folderPath))
                    .andExpect(status().isNotFound());

            verify(folderService, times(1)).deleteFolder(eq(folderPath), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void deleteFolder_MissingPath_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(delete("/api/folders"))
                    .andExpect(status().isBadRequest());

            verify(folderService, never()).deleteFolder(any(), any());
        }
    }

    @Test
    @WithMockUser
    void deleteFolder_EmptyPath_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(delete("/api/folders")
                    .param("path", ""))
                    .andExpect(status().isBadRequest());

            verify(folderService, never()).deleteFolder(any(), any());
        }
    }

    @Test
    void deleteFolder_Unauthenticated_Returns403() throws Exception {
        // When/Then
        mockMvc.perform(delete("/api/folders")
                .param("path", folderPath))
                .andExpect(status().isForbidden());

        verify(folderService, never()).deleteFolder(any(), any());
    }

    private FolderResponse createTestFolderResponse(String path, Integer fileCount) {
        FolderResponse response = new FolderResponse();
        response.setPath(path);
        response.setDescription("Test folder");
        response.setFileCount(fileCount);
        response.setCreatedAt(Instant.now());
        return response;
    }

    private FolderStatisticsResponse createTestFolderStatisticsResponse(String path) {
        Map<String, Long> byContentType = Map.of(
                "image/jpeg", 5L,
                "image/png", 3L,
                "application/pdf", 2L);
        Map<String, Long> byFolder = Map.of(
                "/photos/2024/january", 3L,
                "/photos/2024/february", 7L);

        FolderStatisticsResponse response = new FolderStatisticsResponse();
        response.setPath(path);
        response.setTotalFiles(10);
        response.setTotalSize(1048576L); // 1 MB
        response.setAverageFileSize(104857L); // ~100 KB
        response.setStorageUsed("1.00 MB");
        response.setByContentType(byContentType);
        response.setByFolder(byFolder);
        return response;
    }
}
