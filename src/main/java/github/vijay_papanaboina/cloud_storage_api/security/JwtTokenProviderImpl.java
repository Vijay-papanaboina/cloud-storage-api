package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.model.ClientType;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenProviderImpl implements JwtTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProviderImpl.class);
    private static final int MIN_SECRET_LENGTH = 32; // 256 bits for HS256 algorithm

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        validateSecret();
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT signing key initialized successfully");
    }

    private void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret must not be null or empty");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    String.format("JWT secret must be at least %d characters (256 bits) for HS256 algorithm. " +
                            "Current length: %d characters", MIN_SECRET_LENGTH, jwtSecret.length()));
        }
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    @Override
    public String generateAccessToken(UUID userId, String username, ClientType clientType) {
        ClientType effectiveClientType = clientType != null ? clientType : ClientType.WEB;
        long expiration = getTokenExpiration(effectiveClientType, TokenType.ACCESS);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("clientType", effectiveClientType.name())
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(UUID userId, String username, ClientType clientType) {
        ClientType effectiveClientType = clientType != null ? clientType : ClientType.WEB;
        long expiration = getTokenExpiration(effectiveClientType, TokenType.REFRESH);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("clientType", effectiveClientType.name())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID getUserIdFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        String userIdStr = claims.getSubject();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new InvalidTokenException("Token does not contain a valid user ID");
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid user ID format in token", e);
        }
    }

    @Override
    public String getUsernameFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        String username = claims.get("username", String.class);
        if (username == null || username.isEmpty()) {
            throw new InvalidTokenException("Token does not contain a username");
        }
        return username;
    }

    @Override
    public ClientType getClientTypeFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        String clientTypeStr = claims.get("clientType", String.class);
        // Client type can be null, default to WEB if not present
        if (clientTypeStr == null || clientTypeStr.isEmpty()) {
            return ClientType.WEB;
        }
        try {
            return ClientType.valueOf(clientTypeStr);
        } catch (IllegalArgumentException e) {
            // If invalid value in token, default to WEB
            log.warn("Invalid client type in token: {}, defaulting to WEB", clientTypeStr);
            return ClientType.WEB;
        }
    }

    @Override
    public long getTokenExpiration(ClientType clientType, TokenType tokenType) {
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(tokenType, "tokenType must not be null");

        if (tokenType == TokenType.ACCESS) {
            return clientType.getAccessTokenExpiration().toMillis();
        } else if (tokenType == TokenType.REFRESH) {
            return clientType.getRefreshTokenExpiration().toMillis();
        } else {
            throw new IllegalArgumentException("Unsupported token type: " + tokenType);
        }
    }

    private Claims getClaimsFromToken(String token) throws InvalidTokenException {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired", e);
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException("Token is malformed", e);
        } catch (SecurityException e) {
            throw new InvalidTokenException("Token signature is invalid", e);
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token", e);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Token is null or empty", e);
        }
    }
}
