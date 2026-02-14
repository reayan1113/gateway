package com.example.api_gateway.filter;

import com.example.api_gateway.exception.InsufficientPermissionsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Enforce role-based authorization at path level
 */
@Slf4j
@Component
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Define role-based access rules (path pattern -> allowed roles)
    private static final Map<String, List<String>> ROLE_ACCESS_RULES = Map.of(
            "/api/admin/**", List.of("ADMIN"),
            "/api/kds/**", List.of("ADMIN", "KITCHEN"),
            "/api/analytics/**", List.of("ADMIN")
            // By default, other paths allow all authenticated users
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String roleName = exchange.getAttribute(JwtAuthenticationFilter.ROLE_NAME_ATTRIBUTE);

        // If roleName is null, this is a public path - skip authorization
        if (roleName == null) {
            return chain.filter(exchange);
        }

        // Check if path requires specific role
        for (Map.Entry<String, List<String>> rule : ROLE_ACCESS_RULES.entrySet()) {
            if (pathMatcher.match(rule.getKey(), path)) {
                List<String> allowedRoles = rule.getValue();
                if (!allowedRoles.contains(roleName)) {
                    log.warn("Access denied - User with role {} attempted to access {}",
                            roleName, path);
                    throw new InsufficientPermissionsException(
                            String.format("Role %s is not authorized to access this resource", roleName));
                }
                log.debug("Role {} authorized for path {}", roleName, path);
                break;
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 5; // Execute after HeaderInjectionFilter
    }
}

