package com.example.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Inject identity and context headers into downstream service requests
 */
@Slf4j
@Component
public class HeaderInjectionFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Retrieve attributes from JwtAuthenticationFilter
        Long userId = exchange.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        Long tableId = exchange.getAttribute(JwtAuthenticationFilter.TABLE_ID_ATTRIBUTE);
        String roleName = exchange.getAttribute(JwtAuthenticationFilter.ROLE_NAME_ATTRIBUTE);
        String correlationId = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);

        // If attributes are missing, this is a public path - skip header injection
        if (userId == null || roleName == null) {
            log.debug("Skipping header injection - missing required attributes (userId or roleName)");
            return chain.filter(exchange);
        }

        // Inject headers into downstream request
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header("X-User-Id", userId.toString())
                .header("X-Role", roleName)
                .header("X-Service-Name", "gateway")
                .header("X-Correlation-Id", correlationId != null ? correlationId : "");

        // Only add tableId if present
        if (tableId != null) {
            requestBuilder.header("X-Table-Id", tableId.toString());
        }
        ServerHttpRequest mutatedRequest = requestBuilder.build();

        log.debug("Injected headers - X-User-Id: {}, X-Table-Id: {}, X-Role: {}",
                userId, tableId, roleName);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return 4; // Execute after JwtAuthenticationFilter
    }
}
