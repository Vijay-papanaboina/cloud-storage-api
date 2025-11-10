package github.vijay_papanaboina.cloud_storage_api.dto;

public class RefreshTokenResponse {
    public static final String DEFAULT_TOKEN_TYPE = "Bearer";

    private String accessToken;
    private String tokenType = DEFAULT_TOKEN_TYPE;
    private Long expiresIn;

    // Constructors
    public RefreshTokenResponse() {
    }

    public RefreshTokenResponse(String accessToken, String tokenType, Long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType != null ? tokenType : DEFAULT_TOKEN_TYPE;
        this.expiresIn = expiresIn;
    }

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType != null ? tokenType : DEFAULT_TOKEN_TYPE;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
