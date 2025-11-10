package github.vijay_papanaboina.cloud_storage_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @Pattern(regexp = "^(CLI|WEB)$", message = "Client type must be either 'CLI' or 'WEB'")
    private String clientType = "WEB"; // Default to WEB

    // Constructors
    public LoginRequest() {
    }

    public LoginRequest(String username, String password, String clientType) {
        this.username = username;
        this.password = password;
        this.clientType = clientType != null ? clientType : "WEB";
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType != null ? clientType : "WEB";
    }

    @Override
    public String toString() {
        return "LoginRequest{" +
                "username='" + username + '\'' +
                ", password='[REDACTED]'" +
                ", clientType='" + clientType + '\'' +
                '}';
    }
}
