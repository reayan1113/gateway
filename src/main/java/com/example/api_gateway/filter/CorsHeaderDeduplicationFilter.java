package com.example.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Filter to remove duplicate CORS headers from downstream service responses.
 * 
 * This is needed when both the gateway and downstream services add CORS headers,
 * which causes browsers to reject the response with:
 * "The 'Access-Control-Allow-Origin' header contains multiple values"
 * 
 * This filter ensures only ONE value exists for each CORS header.
 * 
 * @author Ishanka Senadeera
 * @since 2026-02-18
 */
@Slf4j
@Component
public class CorsHeaderDeduplicationFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            
            // Deduplicate CORS headers - keep only the first value
            deduplicateHeader(headers, ACCESS_CONTROL_ALLOW_ORIGIN);
            deduplicateHeader(headers, ACCESS_CONTROL_ALLOW_CREDENTIALS);
            deduplicateHeader(headers, ACCESS_CONTROL_ALLOW_METHODS);
            deduplicateHeader(headers, ACCESS_CONTROL_ALLOW_HEADERS);
            deduplicateHeader(headers, ACCESS_CONTROL_EXPOSE_HEADERS);
            deduplicateHeader(headers, ACCESS_CONTROL_MAX_AGE);
        }));
    }

    private void deduplicateHeader(HttpHeaders headers, String headerName) {
        List<String> values = headers.get(headerName);
        if (values != null && values.size() > 1) {
            log.debug("Deduplicating {} header: {} values -> 1", headerName, values.size());
            String firstValue = values.get(0);
            // If the first value contains comma-separated duplicates, take only the first
            if (firstValue.contains(",")) {
                firstValue = firstValue.split(",")[0].trim();
            }
            headers.set(headerName, firstValue);
        } else if (values != null && values.size() == 1) {
            // Check if single value has comma-separated duplicates
            String value = values.get(0);
            if (value.contains(",")) {
                String[] parts = value.split(",");
                // Check if all parts are the same (duplicate)
                String first = parts[0].trim();
                boolean allSame = true;
                for (String part : parts) {
                    if (!part.trim().equals(first)) {
                        allSame = false;
                        break;
                    }
                }
                if (allSame) {
                    log.debug("Removing duplicate value in {} header", headerName);
                    headers.set(headerName, first);
                }
            }
        }
    }

    @Override
    public int getOrder() {
        // Execute last, after response is built
        return Ordered.LOWEST_PRECEDENCE;
    }
}
