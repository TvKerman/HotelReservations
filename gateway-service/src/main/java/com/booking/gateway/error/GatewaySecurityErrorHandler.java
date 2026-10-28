package com.booking.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class GatewaySecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    public static final String TRACE_HEADER = "X-Correlation-Id";
    private final ObjectMapper objectMapper;

    public GatewaySecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.name());
        body.put("code", code);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("traceId", exchange.getRequest().getHeaders().getFirst(TRACE_HEADER));

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}