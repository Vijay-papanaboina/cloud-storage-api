package github.vijay_papanaboina.cloud_storage_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FileController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileService fileService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID userId;
    private UUID fileId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fileId = UUID.randomUUID();
    }

    // POST /api/files/upload tests

    @Test
    @WithMockUser
    void uploadFile_Success_Returns201() throws Exception {
        // Given
        MockMultipartFile file = createTestMultipartFile("test.txt");
        FileResponse response = createTestFileResponse(fileId, "test.txt");

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.upload(any(), eq(Optional.empty()), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(multipart("/api/files/upload")
                    .file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(fileId.toString()))
                    .andExpect(jsonPath("$.filename").value("test.txt"))
                    .andExpect(jsonPath("$.contentType").value("text/plain"));

            verify(fileService, times(1)).upload(any(), eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void uploadFile_WithFolderPath_Returns201() throws Exception {
        // Given
        MockMultipartFile file = createTestMultipartFile("test.txt");
        String folderPath = "/documents";
        FileResponse response = createTestFileResponse(fileId, "test.txt");
        response.setFolderPath(folderPath);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.upload(any(), eq(Optional.of(folderPath)), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(multipart("/api/files/upload")
                    .file(file)
                    .param("folderPath", folderPath))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.folderPath").value(folderPath));

            verify(fileService, times(1)).upload(any(), eq(Optional.of(folderPath)), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void uploadFile_MissingFile_Returns500() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then - Spring throws MissingServletRequestParameterException which may
            // not be handled
            // by GlobalExceptionHandler in @WebMvcTest context, resulting in 500
            mockMvc.perform(multipart("/api/files/upload"))
                    .andExpect(status().isInternalServerError());

            verify(fileService, never()).upload(any(), any(), any());
        }
    }

    @Test
    void uploadFile_Unauthenticated_Returns401() throws Exception {
        // Given
        MockMultipartFile file = createTestMultipartFile("test.txt");

        // When/Then
        mockMvc.perform(multipart("/api/files/upload")
                .file(file))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).upload(any(), any(), any());
    }

    // GET /api/files tests

    @Test
    @WithMockUser
    void listFiles_Success_Returns200() throws Exception {
        // Given
        List<FileResponse> files = new ArrayList<>();
        files.add(createTestFileResponse(fileId, "file1.txt"));
        Page<FileResponse> page = new PageImpl<>(files, PageRequest.of(0, 20), 1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId))).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].filename").value("file1.txt"));

            verify(fileService, times(1)).list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void listFiles_WithFilters_Returns200() throws Exception {
        // Given
        String contentType = "text/plain";
        String folderPath = "/documents";
        List<FileResponse> files = new ArrayList<>();
        files.add(createTestFileResponse(fileId, "file1.txt"));
        Page<FileResponse> page = new PageImpl<>(files, PageRequest.of(0, 20), 1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.list(any(), eq(Optional.of(contentType)), eq(Optional.of(folderPath)), eq(userId)))
                    .thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files")
                    .param("contentType", contentType)
                    .param("folderPath", folderPath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(fileService, times(1)).list(any(), eq(Optional.of(contentType)), eq(Optional.of(folderPath)),
                    eq(userId));
        }
    }

    @Test
    @WithMockUser
    void listFiles_WithSort_Returns200() throws Exception {
        // Given
        List<FileResponse> files = new ArrayList<>();
        files.add(createTestFileResponse(fileId, "file1.txt"));
        Page<FileResponse> page = new PageImpl<>(files, PageRequest.of(0, 20), 1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId))).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files")
                    .param("sort", "filename,asc"))
                    .andExpect(status().isOk());

            verify(fileService, times(1)).list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void listFiles_InvalidPage_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/files")
                    .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).list(any(), any(), any(), any());
        }
    }

    @Test
    @WithMockUser
    void listFiles_InvalidSize_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then - size > 100
            mockMvc.perform(get("/api/files")
                    .param("size", "101"))
                    .andExpect(status().isBadRequest());

            // When/Then - size <= 0
            mockMvc.perform(get("/api/files")
                    .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).list(any(), any(), any(), any());
        }
    }

    @Test
    @WithMockUser
    void listFiles_EmptyResult_Returns200() throws Exception {
        // Given
        Page<FileResponse> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId))).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(fileService, times(1)).list(any(), eq(Optional.empty()), eq(Optional.empty()), eq(userId));
        }
    }

    @Test
    void listFiles_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).list(any(), any(), any(), any());
    }

    // GET /api/files/{id} tests

    @Test
    @WithMockUser
    void getFile_Success_Returns200() throws Exception {
        // Given
        FileResponse response = createTestFileResponse(fileId, "test.txt");

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getById(eq(fileId), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/files/{id}", fileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(fileId.toString()))
                    .andExpect(jsonPath("$.filename").value("test.txt"));

            verify(fileService, times(1)).getById(eq(fileId), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void getFile_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getById(eq(fileId), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("File not found: " + fileId));

            // When/Then
            mockMvc.perform(get("/api/files/{id}", fileId))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).getById(eq(fileId), eq(userId));
        }
    }

    @Test
    void getFile_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/{id}", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).getById(any(), any());
    }

    // GET /api/files/{id}/url tests

    @Test
    @WithMockUser
    void getFileUrl_Success_Returns200() throws Exception {
        // Given
        FileUrlResponse response = createTestFileUrlResponse(fileId);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getSignedDownloadUrl(eq(fileId), eq(userId), eq(60))).thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/url", fileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").exists())
                    .andExpect(jsonPath("$.publicId").exists());

            verify(fileService, times(1)).getSignedDownloadUrl(eq(fileId), eq(userId), eq(60));
        }
    }

    @Test
    @WithMockUser
    void getFileUrl_WithCustomExpiration_Returns200() throws Exception {
        // Given
        int expirationMinutes = 120;
        FileUrlResponse response = createTestFileUrlResponse(fileId);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getSignedDownloadUrl(eq(fileId), eq(userId), eq(expirationMinutes))).thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/url", fileId)
                    .param("expirationMinutes", String.valueOf(expirationMinutes)))
                    .andExpect(status().isOk());

            verify(fileService, times(1)).getSignedDownloadUrl(eq(fileId), eq(userId), eq(expirationMinutes));
        }
    }

    @Test
    @WithMockUser
    void getFileUrl_NegativeExpiration_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/url", fileId)
                    .param("expirationMinutes", "0"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/files/{id}/url", fileId)
                    .param("expirationMinutes", "-1"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).getSignedDownloadUrl(any(), any(), anyInt());
        }
    }

    @Test
    @WithMockUser
    void getFileUrl_ExceedsMaxExpiration_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/url", fileId)
                    .param("expirationMinutes", "1441"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).getSignedDownloadUrl(any(), any(), anyInt());
        }
    }

    @Test
    void getFileUrl_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/{id}/url", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).getSignedDownloadUrl(any(), any(), anyInt());
    }

    // GET /api/files/{id}/download tests

    @Test
    @WithMockUser
    void downloadFile_Success_Returns200() throws Exception {
        // Given
        FileResponse fileMetadata = createTestFileResponse(fileId, "test.txt");
        Resource mockResource = mock(Resource.class);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getById(eq(fileId), eq(userId))).thenReturn(fileMetadata);
            when(fileService.download(eq(fileId), eq(userId))).thenReturn(mockResource);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/download", fileId))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"));

            verify(fileService, times(1)).getById(eq(fileId), eq(userId));
            verify(fileService, times(1)).download(eq(fileId), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void downloadFile_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getById(eq(fileId), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("File not found: " + fileId));

            // When/Then
            mockMvc.perform(get("/api/files/{id}/download", fileId))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).getById(eq(fileId), eq(userId));
            verify(fileService, never()).download(any(), any());
        }
    }

    @Test
    void downloadFile_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/{id}/download", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).getById(any(), any());
        verify(fileService, never()).download(any(), any());
    }

    // PUT /api/files/{id} tests

    @Test
    @WithMockUser
    void updateFile_Success_Returns200() throws Exception {
        // Given
        FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", "/documents");
        FileResponse response = createTestFileResponse(fileId, "new-filename.txt");
        response.setFolderPath("/documents");

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.update(eq(fileId), any(FileUpdateRequest.class), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(put("/api/files/{id}", fileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filename").value("new-filename.txt"))
                    .andExpect(jsonPath("$.folderPath").value("/documents"));

            verify(fileService, times(1)).update(eq(fileId), any(FileUpdateRequest.class), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void updateFile_InvalidFilename_Returns400() throws Exception {
        // Given
        String longFilename = "a".repeat(256); // Exceeds 255 character limit
        FileUpdateRequest request = createTestFileUpdateRequest(longFilename, null);

        // When/Then
        mockMvc.perform(put("/api/files/{id}", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).update(any(), any(), any());
    }

    @Test
    @WithMockUser
    void updateFile_NotFound_Returns404() throws Exception {
        // Given
        FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.update(eq(fileId), any(FileUpdateRequest.class), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("File not found: " + fileId));

            // When/Then
            mockMvc.perform(put("/api/files/{id}", fileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).update(eq(fileId), any(FileUpdateRequest.class), eq(userId));
        }
    }

    @Test
    void updateFile_Unauthenticated_Returns401() throws Exception {
        // Given
        FileUpdateRequest request = createTestFileUpdateRequest("new-filename.txt", null);

        // When/Then
        mockMvc.perform(put("/api/files/{id}", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).update(any(), any(), any());
    }

    // DELETE /api/files/{id} tests

    @Test
    @WithMockUser
    void deleteFile_Success_Returns204() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doNothing().when(fileService).delete(eq(fileId), eq(userId));

            // When/Then
            mockMvc.perform(delete("/api/files/{id}", fileId))
                    .andExpect(status().isNoContent());

            verify(fileService, times(1)).delete(eq(fileId), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void deleteFile_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            doThrow(new ResourceNotFoundException("File not found: " + fileId))
                    .when(fileService).delete(eq(fileId), eq(userId));

            // When/Then
            mockMvc.perform(delete("/api/files/{id}", fileId))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).delete(eq(fileId), eq(userId));
        }
    }

    @Test
    void deleteFile_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(delete("/api/files/{id}", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).delete(any(), any());
    }

    // POST /api/files/{id}/transform tests

    @Test
    @WithMockUser
    void transformFile_Success_Returns200() throws Exception {
        // Given
        TransformRequest request = createTestTransformRequest(800, 600);
        TransformResponse response = createTestTransformResponse(fileId);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.transform(eq(fileId), any(TransformRequest.class), eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/files/{id}/transform", fileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transformedUrl").exists())
                    .andExpect(jsonPath("$.originalUrl").exists());

            verify(fileService, times(1)).transform(eq(fileId), any(TransformRequest.class), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void transformFile_InvalidRequest_Returns400() throws Exception {
        // Given - Invalid width (must be >= 1)
        TransformRequest request = new TransformRequest();
        request.setWidth(0);
        request.setHeight(600);

        // When/Then
        mockMvc.perform(post("/api/files/{id}/transform", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).transform(any(), any(), any());
    }

    @Test
    @WithMockUser
    void transformFile_NotFound_Returns404() throws Exception {
        // Given
        TransformRequest request = createTestTransformRequest(800, 600);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.transform(eq(fileId), any(TransformRequest.class), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("File not found: " + fileId));

            // When/Then
            mockMvc.perform(post("/api/files/{id}/transform", fileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).transform(eq(fileId), any(TransformRequest.class), eq(userId));
        }
    }

    @Test
    void transformFile_Unauthenticated_Returns401() throws Exception {
        // Given
        TransformRequest request = createTestTransformRequest(800, 600);

        // When/Then
        mockMvc.perform(post("/api/files/{id}/transform", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).transform(any(), any(), any());
    }

    // GET /api/files/{id}/transform tests

    @Test
    @WithMockUser
    void getTransformUrl_Success_Returns200() throws Exception {
        // Given
        TransformResponse response = createTestTransformResponse(fileId);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getTransformUrl(eq(fileId), eq(800), eq(600), any(), any(), any(), eq(userId)))
                    .thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/files/{id}/transform", fileId)
                    .param("width", "800")
                    .param("height", "600")
                    .param("crop", "fill")
                    .param("quality", "auto")
                    .param("format", "webp"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transformedUrl").exists());

            verify(fileService, times(1)).getTransformUrl(eq(fileId), eq(800), eq(600), any(), any(), any(),
                    eq(userId));
        }
    }

    @Test
    @WithMockUser
    void getTransformUrl_NotFound_Returns404() throws Exception {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getTransformUrl(eq(fileId), isNull(), isNull(), any(), any(), any(), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("File not found: " + fileId));

            // When/Then
            mockMvc.perform(get("/api/files/{id}/transform", fileId))
                    .andExpect(status().isNotFound());

            verify(fileService, times(1)).getTransformUrl(eq(fileId), isNull(), isNull(), any(), any(), any(),
                    eq(userId));
        }
    }

    @Test
    void getTransformUrl_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/{id}/transform", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).getTransformUrl(any(), any(), any(), any(), any(), any(), any());
    }

    // GET /api/files/search tests

    @Test
    @WithMockUser
    void searchFiles_Success_Returns200() throws Exception {
        // Given
        String query = "test";
        List<FileResponse> files = new ArrayList<>();
        files.add(createTestFileResponse(fileId, "test.txt"));
        Page<FileResponse> page = new PageImpl<>(files, PageRequest.of(0, 20), 1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.search(eq(query), eq(Optional.empty()), eq(Optional.empty()), any(), eq(userId)))
                    .thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files/search")
                    .param("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(fileService, times(1)).search(eq(query), eq(Optional.empty()), eq(Optional.empty()), any(),
                    eq(userId));
        }
    }

    @Test
    @WithMockUser
    void searchFiles_WithFilters_Returns200() throws Exception {
        // Given
        String query = "test";
        String contentType = "text/plain";
        String folderPath = "/documents";
        List<FileResponse> files = new ArrayList<>();
        files.add(createTestFileResponse(fileId, "test.txt"));
        Page<FileResponse> page = new PageImpl<>(files, PageRequest.of(0, 20), 1);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.search(eq(query), eq(Optional.of(contentType)), eq(Optional.of(folderPath)), any(),
                    eq(userId))).thenReturn(page);

            // When/Then
            mockMvc.perform(get("/api/files/search")
                    .param("q", query)
                    .param("contentType", contentType)
                    .param("folderPath", folderPath))
                    .andExpect(status().isOk());

            verify(fileService, times(1)).search(eq(query), eq(Optional.of(contentType)),
                    eq(Optional.of(folderPath)), any(), eq(userId));
        }
    }

    @Test
    @WithMockUser
    void searchFiles_MissingQuery_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/files/search"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).search(any(), any(), any(), any(), any());
        }
    }

    @Test
    @WithMockUser
    void searchFiles_EmptyQuery_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then
            mockMvc.perform(get("/api/files/search")
                    .param("q", ""))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/files/search")
                    .param("q", "   "))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).search(any(), any(), any(), any(), any());
        }
    }

    @Test
    @WithMockUser
    void searchFiles_InvalidPagination_Returns400() throws Exception {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);

            // When/Then - negative page
            mockMvc.perform(get("/api/files/search")
                    .param("q", "test")
                    .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            // When/Then - size > 100
            mockMvc.perform(get("/api/files/search")
                    .param("q", "test")
                    .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verify(fileService, never()).search(any(), any(), any(), any(), any());
        }
    }

    @Test
    void searchFiles_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/search")
                .param("q", "test"))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).search(any(), any(), any(), any(), any());
    }

    // GET /api/files/statistics tests

    @Test
    @WithMockUser
    void getFileStatistics_Success_Returns200() throws Exception {
        // Given
        FileStatisticsResponse response = createTestFileStatisticsResponse();

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(userId);
            when(fileService.getStatistics(eq(userId))).thenReturn(response);

            // When/Then
            mockMvc.perform(get("/api/files/statistics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalFiles").value(10))
                    .andExpect(jsonPath("$.totalSize").value(1048576))
                    .andExpect(jsonPath("$.storageUsed").exists())
                    .andExpect(jsonPath("$.byContentType").exists())
                    .andExpect(jsonPath("$.byFolder").exists());

            verify(fileService, times(1)).getStatistics(eq(userId));
        }
    }

    @Test
    void getFileStatistics_Unauthenticated_Returns401() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/files/statistics"))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).getStatistics(any());
    }

    // Helper methods
    private FileResponse createTestFileResponse(UUID fileId, String filename) {
        FileResponse response = new FileResponse();
        response.setId(fileId);
        response.setFilename(filename);
        response.setContentType("text/plain");
        response.setFileSize(1024L);
        response.setFolderPath("/documents");
        response.setCloudinaryUrl("http://res.cloudinary.com/test/image/upload/test.jpg");
        response.setCloudinarySecureUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
        response.setCreatedAt(Instant.now());
        response.setUpdatedAt(Instant.now());
        return response;
    }

    private FileUrlResponse createTestFileUrlResponse(UUID fileId) {
        FileUrlResponse response = new FileUrlResponse();
        response.setUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
        response.setPublicId("test");
        response.setFormat("jpg");
        response.setResourceType("image");
        response.setExpiresAt(Instant.now().plusSeconds(3600));
        return response;
    }

    private TransformResponse createTestTransformResponse(UUID fileId) {
        TransformResponse response = new TransformResponse();
        response.setTransformedUrl("https://res.cloudinary.com/test/image/upload/w_800,h_600/test.jpg");
        response.setOriginalUrl("https://res.cloudinary.com/test/image/upload/test.jpg");
        return response;
    }

    private FileStatisticsResponse createTestFileStatisticsResponse() {
        Map<String, Long> byContentType = Map.of(
                "text/plain", 5L,
                "image/jpeg", 3L,
                "application/pdf", 2L);
        Map<String, Long> byFolder = Map.of(
                "/documents", 7L,
                "/photos", 3L);

        return new FileStatisticsResponse(
                10L,
                1048576L, // 1 MB
                104857L, // ~100 KB average
                "1.00 MB",
                byContentType,
                byFolder);
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

    private MockMultipartFile createTestMultipartFile(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                MediaType.TEXT_PLAIN_VALUE,
                "Test file content".getBytes());
    }
}
