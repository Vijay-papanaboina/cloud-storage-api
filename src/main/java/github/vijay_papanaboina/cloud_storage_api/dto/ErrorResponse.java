package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private Instant timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
    private String errorCode;
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

    // Builder pattern to avoid constructor ambiguity
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Instant timestamp;
        private Integer status;
        private String error;
        private String message;
        private String path;
        private String errorCode;
        private List<ValidationErrorResponse> details;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(Integer status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder details(List<ValidationErrorResponse> details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse();
            // Set timestamp to current time if not explicitly set, ensuring it reflects
            // actual build time
            response.timestamp = (this.timestamp != null) ? this.timestamp : Instant.now();
            response.status = this.status;
            response.error = this.error;
            response.message = this.message;
            response.path = this.path;
            response.errorCode = this.errorCode;
            response.details = this.details;
            return response;
        }
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
