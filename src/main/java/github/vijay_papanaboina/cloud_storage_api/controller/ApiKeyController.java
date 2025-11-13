package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.UserResponse;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for API key operations.
 * Handles API key verification and related operations.
 */
@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final AuthService authService;

    @Autowired
    public ApiKeyController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Verify API key endpoint.
     * Validates the API key from X-API-Key header and returns user details.
     * The API key is validated by ApiKeyAuthenticationFilter before reaching this
     * endpoint.
     *
     * @return UserResponse with user information if API key is valid
     */
    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verifyApiKey() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        if (userId == null) {
            return ResponseEntity.status(404).build();
        }
        UserResponse response = authService.getCurrentUser(userId);
        if (response == null) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(response);
    }
}
