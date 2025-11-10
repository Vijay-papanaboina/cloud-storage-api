package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private Instant timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
    private List<ValidationErrorResponse> details;

    // Constructors
    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(Integer status, String error, String message, String path) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(Integer status, String error, String message, String path,
            List<ValidationErrorResponse> details) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.details = details;
    }

    // Getters and Setters
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<ValidationErrorResponse> getDetails() {
        return details;
    }

    public void setDetails(List<ValidationErrorResponse> details) {
        this.details = details;
    }
}
