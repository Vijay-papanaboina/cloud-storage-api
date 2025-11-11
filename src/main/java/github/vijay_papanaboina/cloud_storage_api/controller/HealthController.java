package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for health check endpoints.
 * Provides simple health check functionality for monitoring and load balancers.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Health check endpoint.
     * Returns the status of the API service.
     *
     * @return HealthResponse with status "UP" and current timestamp
     */
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = new HealthResponse("UP", Instant.now());
        return ResponseEntity.ok(response);
    }
}

