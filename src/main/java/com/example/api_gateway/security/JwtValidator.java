package com.example.api_gateway.security;

import com.example.api_gateway.exception.JwtAuthenticationException;
import com.example.api_gateway.exception.MissingClaimException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Slf4j
@Component
public class JwtValidator {

    private final Key signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate JWT token and extract claims
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Validate mandatory claims
            if (claims.getSubject() == null || claims.getSubject().isEmpty()) {
                throw new MissingClaimException("JWT subject (userId) is missing");
            }

            if (claims.get("role") == null) {
                throw new MissingClaimException("JWT role claim is missing");
            }

            log.debug("JWT validated successfully for userId: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
            throw new JwtAuthenticationException("Token has expired", e);
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid token", e);
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation: {}", e.getMessage());
            throw new JwtAuthenticationException("Token validation failed", e);
        }
    }

    /**
     * Extract userId from JWT claims
     */
    public Long extractUserId(Claims claims) {
        String subject = claims.getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new JwtAuthenticationException("Invalid userId format in token");
        }
    }

    /**
     * Extract role from JWT claims
     */
    public Integer extractRole(Claims claims) {
        Object roleObj = claims.get("role");
        if (roleObj instanceof Integer) {
            return (Integer) roleObj;
        }
        throw new MissingClaimException("Role claim has invalid format");
    }

    /**
     * Extract tableId from JWT claims (optional claim)
     */
    public Long extractTableId(Claims claims) {
        Object tableIdObj = claims.get("tableId");
        if (tableIdObj == null) {
            return null; // TableId not in JWT
        }

        try {
            if (tableIdObj instanceof Integer) {
                return ((Integer) tableIdObj).longValue();
            } else if (tableIdObj instanceof Long) {
                return (Long) tableIdObj;
            } else if (tableIdObj instanceof String) {
                return Long.parseLong((String) tableIdObj);
            }
            log.warn("Unexpected tableId type in JWT: {}", tableIdObj.getClass().getName());
            return null;
        } catch (NumberFormatException e) {
            log.warn("Invalid tableId format in JWT: {}", tableIdObj);
            return null;
        }
    }

    /**
     * Convert role integer to string representation
     */
    public String getRoleName(Integer role) {
        return switch (role) {
            case 1 -> "CUSTOMER";
            case 2 -> "ADMIN";
            case 3 -> "KITCHEN";
            default -> "UNKNOWN";
        };
    }
}

