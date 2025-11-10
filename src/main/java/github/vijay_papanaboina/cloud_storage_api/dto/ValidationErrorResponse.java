package github.vijay_papanaboina.cloud_storage_api.dto;

public class ValidationErrorResponse {
    private String field;
    private String message;

    // Constructors
    public ValidationErrorResponse() {
    }

    public ValidationErrorResponse(String field, String message) {
        this.field = field;
        this.message = message;
    }

    // Getters and Setters
    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
