package com.example.api_gateway.filter;

import com.example.api_gateway.exception.JwtAuthenticationException;
import com.example.api_gateway.exception.MissingClaimException;
import com.example.api_gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Validate JWT token, extract claims, and validate tableId
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String TABLE_ID_ATTRIBUTE = "tableId";
    public static final String ROLE_ATTRIBUTE = "role";
    public static final String ROLE_NAME_ATTRIBUTE = "roleName";

    public JwtAuthenticationFilter(
            JwtValidator jwtValidator,
            @Value("${gateway.public-paths:/api/auth/**}") String publicPathsStr) {
        this.jwtValidator = jwtValidator;
        this.publicPaths = Arrays.asList(publicPathsStr.split(","));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip JWT validation for public paths
        if (isPublicPath(path)) {
            log.debug("Skipping JWT validation for public path: {}", path);
            return chain.filter(exchange);
        }

        // Extract JWT from Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            throw new JwtAuthenticationException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            // Validate JWT and extract claims
            Claims claims = jwtValidator.validateAndExtractClaims(token);
            Long userId = jwtValidator.extractUserId(claims);
            Integer role = jwtValidator.extractRole(claims);
            String roleName = jwtValidator.getRoleName(role);

            log.debug("JWT validated - userId: {}, role: {}", userId, roleName);

            // Extract tableId - Priority 1: JWT claims, Priority 2: Request (header/query param)
            Long tableId = jwtValidator.extractTableId(claims);
            if (tableId == null) {
                log.debug("TableId not found in JWT, checking request headers/params");
                tableId = extractTableIdFromRequest(request);
            } else {
                log.debug("TableId extracted from JWT: {}", tableId);
            }

            if (tableId == null) {
                log.warn("Missing tableId for protected path: {}", path);
                throw new MissingClaimException("TableId must be present in JWT claims or X-Table-Id header/query parameter");
            }

            log.debug("Final tableId value: {}", tableId);

            // Store in exchange attributes for downstream filters
            exchange.getAttributes().put(USER_ID_ATTRIBUTE, userId);
            exchange.getAttributes().put(TABLE_ID_ATTRIBUTE, tableId);
            exchange.getAttributes().put(ROLE_ATTRIBUTE, role);
            exchange.getAttributes().put(ROLE_NAME_ATTRIBUTE, roleName);

            return chain.filter(exchange);

        } catch (JwtAuthenticationException | MissingClaimException e) {
            throw e; // Will be caught by GlobalErrorHandler
        } catch (Exception e) {
            log.error("Unexpected error in JWT authentication: {}", e.getMessage());
            throw new JwtAuthenticationException("Authentication failed", e);
        }
    }

    /**
     * Check if the path is public (doesn't require JWT)
     */
    private boolean isPublicPath(String path) {
        return publicPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Extract tableId from request (X-Table-Id header or tableId query parameter)
     * This is a fallback when tableId is not in JWT claims
     */
    private Long extractTableIdFromRequest(ServerHttpRequest request) {
        // Priority 1: X-Table-Id header
        String headerTableId = request.getHeaders().getFirst("X-Table-Id");
        if (headerTableId != null && !headerTableId.isEmpty()) {
            try {
                return Long.parseLong(headerTableId);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Table-Id header format: {}", headerTableId);
            }
        }

        // Priority 2: tableId query parameter
        String queryTableId = request.getQueryParams().getFirst("tableId");
        if (queryTableId != null && !queryTableId.isEmpty()) {
            try {
                return Long.parseLong(queryTableId);
            } catch (NumberFormatException e) {
                log.warn("Invalid tableId query parameter format: {}", queryTableId);
            }
        }

        return null;
    }

    @Override
    public int getOrder() {
        return 3; // Execute after LoggingFilter
    }
}

