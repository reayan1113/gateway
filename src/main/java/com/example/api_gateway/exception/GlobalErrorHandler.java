package com.example.api_gateway.exception;

import com.example.api_gateway.dto.ErrorResponse;
import com.example.api_gateway.filter.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Global error handler for the API Gateway
 * Normalizes error responses across all endpoints
 */
@Slf4j
@Component
@Order(-2) // Higher priority than default error handler
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);

        HttpStatus status;
        String message;
        String error;

        // Map exceptions to HTTP status codes
        if (ex instanceof JwtAuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            error = "Unauthorized";
            message = ex.getMessage();
            log.warn("[{}] Authentication failed for {}: {}", correlationId, path, message);
        } else if (ex instanceof MissingClaimException) {
            status = HttpStatus.BAD_REQUEST;
            error = "Bad Request";
            message = ex.getMessage();
            log.warn("[{}] Missing required claim for {}: {}", correlationId, path, message);
        } else if (ex instanceof InsufficientPermissionsException) {
            status = HttpStatus.FORBIDDEN;
            error = "Forbidden";
            message = ex.getMessage();
            log.warn("[{}] Authorization failed for {}: {}", correlationId, path, message);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            error = "Internal Server Error";
            message = "An unexpected error occurred";
            log.error("[{}] Unexpected error for {}: ", correlationId, path, ex);
        }

        // Build error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        // Set response status and content type
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Add correlation ID to response headers
        if (correlationId != null) {
            exchange.getResponse().getHeaders().add("X-Correlation-Id", correlationId);
        }

        // Write error response body
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response: ", e);
            return exchange.getResponse().setComplete();
        }
    }
}

