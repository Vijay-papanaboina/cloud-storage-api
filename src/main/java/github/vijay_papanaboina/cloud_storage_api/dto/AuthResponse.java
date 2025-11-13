package github.vijay_papanaboina.cloud_storage_api.dto;

public class AuthResponse {
    public static final String DEFAULT_TOKEN_TYPE = "Bearer";

    private String accessToken;
    private String refreshToken;
    private String tokenType = DEFAULT_TOKEN_TYPE;
    private Long expiresIn;
    private Long refreshExpiresIn;
    private UserResponse user;

    // Constructors
    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn,
            Long refreshExpiresIn, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType != null ? tokenType : DEFAULT_TOKEN_TYPE;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
        this.user = user;
    }

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
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

    public Long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public void setRefreshExpiresIn(Long refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    @Override
    public String toString() {
        String userInfo = "null";
        if (user != null) {
            if (user.getId() != null) {
                userInfo = "id=" + user.getId();
            } else if (user.getUsername() != null) {
                userInfo = "username=" + user.getUsername();
            }
        }
        return "AuthResponse{" +
                "tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshExpiresIn=" + refreshExpiresIn +
                ", user=" + userInfo +
                '}';
    }
}
