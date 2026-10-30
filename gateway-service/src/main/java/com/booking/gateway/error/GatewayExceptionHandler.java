package com.booking.gateway.error;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);
  public static final String TRACE_HEADER = "X-Correlation-Id";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }

    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String message = "Internal gateway error";

    if (ex instanceof ResponseStatusException rse) {
      status = HttpStatus.resolve(rse.getStatusCode().value());
      if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
      message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
    } else if (ex.getMessage() != null) {
      message = ex.getMessage();
    }

    log.warn(
        "gateway {} {} -> {}: {}",
        exchange.getRequest().getMethod(),
        exchange.getRequest().getPath(),
        status.value(),
        message);

    String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);

    Map<String, Object> body = new HashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.name());
    body.put("code", mapCode(status));
    body.put("message", message);
    body.put("path", exchange.getRequest().getPath().value());
    body.put("traceId", traceId);

    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(body);
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception e) {
      log.error("Failed to serialize error response", e);
      return exchange.getResponse().setComplete();
    }
  }

  private String mapCode(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "BAD_REQUEST";
      case UNAUTHORIZED -> "UNAUTHORIZED";
      case FORBIDDEN -> "FORBIDDEN";
      case NOT_FOUND -> "NOT_FOUND";
      case CONFLICT -> "CONFLICT";
      case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
      default -> "INTERNAL_ERROR";
    };
  }
}
